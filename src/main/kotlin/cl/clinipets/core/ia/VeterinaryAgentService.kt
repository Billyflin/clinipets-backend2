package cl.clinipets.core.ia

import cl.clinipets.agendamiento.application.DisponibilidadService
import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.core.ia.tools.AgentTool
import cl.clinipets.core.ia.tools.ToolContext
import cl.clinipets.core.ia.tools.ToolExecutionResult
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.openapi.models.DashboardResponse
import cl.clinipets.servicios.api.toDto
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.api.toResponse
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.genai.types.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class AgentResponse {
    data class Text(val content: String, val paymentUrl: String? = null) : AgentResponse()
    data class ListOptions(
        val text: String,
        val buttonLabel: String,
        val options: Map<String, String>
    ) : AgentResponse()
}

private data class IaDashboardResult(
    val mensaje: String,
    val serviciosIds: List<String>
)

@Service
class VeterinaryAgentService(
    private val userRepository: UserRepository,
    private val mascotaRepository: MascotaRepository,
    private val disponibilidadService: DisponibilidadService,
    private val reservaService: ReservaService,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val fichaClinicaRepository: FichaClinicaRepository,
    private val clinicZoneId: ZoneId,
    private val otpService: cl.clinipets.identity.application.OtpService,
    private val client: GenAiClientWrapper,
    @Value("\${ia.enabled:true}") private val iaEnabled: Boolean,
    @Value("\${gh.model:openai/gpt-5-nano}") private val modelName: String,
    @Value("\${gh.max-output-tokens:256}") private val maxOutputTokens: Int,
    @Value("\${gh.temperature:0.2}") private val temperature: Float,
    private val agentTools: List<AgentTool>
) {
    private val logger = LoggerFactory.getLogger(VeterinaryAgentService::class.java)
    private val chatHistory = ConcurrentHashMap<String, MutableList<Content>>()
    private val mapper = jacksonObjectMapper()

    init {
        logger.info("[IA_INIT] Servicio Agente Veterinario listo. Modelo: $modelName. Tools cargadas: ${agentTools.size}")
    }

    fun procesarMensaje(telefono: String, mensajeUsuario: String): AgentResponse {
        logger.info("[IA_AGENT] Procesando mensaje para teléfono: $telefono")

        if (!iaEnabled) {
            return AgentResponse.Text("Hola, por ahora el asistente virtual está en pausa. Escríbenos por WhatsApp o llama a la clínica y te ayudamos al tiro.")
        }

        val mensaje = mensajeUsuario.trim()
        if (mensaje.isEmpty()) {
            return AgentResponse.Text("¿En qué te ayudo? Puedo agendar horas o darte precios de servicios.")
        }

        val historial = prepararHistorial(telefono)
        val user = buscarUsuarioPorTelefono(telefono)
        val contextoCliente = construirContextoCliente(user)
        val listaServicios = construirListadoServicios()
        val systemInstructionContent = construirSystemInstruction(contextoCliente, listaServicios, telefono)

        val tools = listOf(
            Tool.builder()
                .functionDeclarations(agentTools.map { it.getFunctionDeclaration() })
                .build()
        )
        
        val config = construirConfigInicial(systemInstructionContent, tools)

        val userContent = construirUserContent(mensajeUsuario)
        val mensajesParaEnviar = ArrayList(historial).apply { add(userContent) }

        return try {
            val primeraRespuesta = client.generateContent(modelName, mensajesParaEnviar, config)
            procesarRespuestaDelModelo(
                respuesta = primeraRespuesta,
                telefono = telefono,
                user = user,
                userContent = userContent,
                mensajesParaEnviar = mensajesParaEnviar,
                historial = historial,
                systemInstructionContent = systemInstructionContent,
                tools = tools
            )
        } catch (e: Exception) {
            logger.error("[IA_AGENT] Error al procesar mensaje", e)
            AgentResponse.Text("Disculpa, estoy teniendo un problema técnico momentáneo.")
        }
    }

    private fun procesarRespuestaDelModelo(
        respuesta: GenerateContentResponse,
        telefono: String,
        user: User?,
        userContent: Content,
        mensajesParaEnviar: List<Content>,
        historial: MutableList<Content>,
        systemInstructionContent: Content,
        tools: List<Tool>
    ): AgentResponse {
        val candidate = respuesta.candidates().orElse(Collections.emptyList()).firstOrNull()
        val modelContent = candidate?.content()?.orElse(null)
        val parts = modelContent?.parts()?.orElse(Collections.emptyList()) ?: emptyList()

        val functionCall = parts.firstOrNull { it.functionCall().isPresent }?.functionCall()?.orElse(null)

        return if (functionCall != null) {
            manejarFunctionCall(
                functionCall = functionCall,
                modelContent = modelContent,
                user = user,
                telefono = telefono,
                userContent = userContent,
                mensajesParaEnviar = mensajesParaEnviar,
                historial = historial,
                systemInstructionContent = systemInstructionContent,
                tools = tools
            )
        } else {
            val textoFinal = respuesta.text() ?: "Lo siento, no pude entenderte."
            registrarConversacion(historial, userContent, textoFinal)
            AgentResponse.Text(textoFinal)
        }
    }

    private fun manejarFunctionCall(
        functionCall: FunctionCall,
        modelContent: Content?,
        user: User?,
        telefono: String,
        userContent: Content,
        mensajesParaEnviar: List<Content>,
        historial: MutableList<Content>,
        systemInstructionContent: Content,
        tools: List<Tool>
    ): AgentResponse {
        val funcName = functionCall.name().orElse("unknown")
        logger.info("[IA_AGENT] La IA solicita ejecutar herramienta: $funcName")

        intentarRespuestaConLista(functionCall, userContent, historial)?.let { return it }

        val toolResult = ejecutarHerramienta(functionCall, user, telefono)
        logger.warn("[DEBUG_PAGO] ToolResult recibido en procesarMensaje. URL: {}", toolResult.paymentUrl)

        val toolContent = Content.builder()
            .role("function")
            .parts(listOf(toolResult.toPart(funcName)))
            .build()

        val historialConTool = ArrayList(mensajesParaEnviar)
        modelContent?.let { historialConTool.add(it) }
        historialConTool.add(toolContent)

        val finalConfig = GenerateContentConfig.builder()
            .systemInstruction(systemInstructionContent)
            .tools(tools)
            .build()

        val response2 = client.generateContent(modelName, historialConTool, finalConfig)
        val textoFinal = response2.text() ?: "No pude completar la acción."

        logger.warn("[DEBUG_PAGO] Construyendo AgentResponse.Text. URL a inyectar: {}", toolResult.paymentUrl)
        registrarConversacion(historial, userContent, textoFinal)
        return AgentResponse.Text(textoFinal, paymentUrl = toolResult.paymentUrl)
    }

    private fun intentarRespuestaConLista(
        functionCall: FunctionCall,
        userContent: Content,
        historial: MutableList<Content>
    ): AgentResponse.ListOptions? {
        val nombreFuncion = functionCall.name().orElse("")
        val argsMap = functionCall.args().orElse(Collections.emptyMap())

        return when (nombreFuncion) {
            "consultar_disponibilidad" -> {
                val fechaStr = argsMap["fecha"] as? String ?: return null
                val servicioStr = argsMap["servicio"] as? String ?: return null
                construirListaInteractiva(fechaStr, servicioStr, userContent, historial)
            }

            "buscar_primera_disponibilidad" -> {
                val servicioStr = argsMap["servicio"] as? String ?: return null
                val primera = buscarPrimeraDisponibilidad(servicioStr) ?: return null
                construirListaInteractiva(primera.first.toString(), servicioStr, userContent, historial, primera.second)
            }

            else -> null
        }
    }

    private fun construirListaInteractiva(
        fechaStr: String,
        servicioStr: String,
        userContent: Content,
        historial: MutableList<Content>,
        slotsPrecalculados: List<Instant>? = null
    ): AgentResponse.ListOptions? {
        return runCatching {
            val fecha = LocalDate.parse(fechaStr)
            val servicioDb = buscarServicio(servicioStr)
            val duracion = servicioDb?.duracionMinutos ?: 30
            val nombreServicio = servicioDb?.nombre ?: "Consulta General"
            val precioTotal = servicioDb?.precioBase ?: 0
            val precioAbono = servicioDb?.precioAbono ?: 0

            val slots = slotsPrecalculados ?: disponibilidadService.obtenerSlots(fecha, duracion)
            if (slots.isEmpty()) return null

            val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(clinicZoneId)
            val optionsMap = slots.associate { slot ->
                val horaStr = formatter.format(slot)
                val id = "RES_${fechaStr}_$horaStr"
                id to horaStr
            }.toMutableMap()
            optionsMap["OTRA_FECHA"] = "Otra fecha"

            val primeraHora = slots.minOrNull()?.let { formatter.format(it) }

            val textoRespuesta =
                "Encontré horas para $nombreServicio ($duracion min) el $fechaStr. " +
                        (primeraHora?.let { "La más cercana es a las $it. " } ?: "") + 
                        "Valor Total: $precioTotal (Abono online: $precioAbono). " +
                        "Elige una hora o pide otra fecha:"

            historial.add(userContent)
            historial.add(
                Content.builder()
                    .role("model")
                    .parts(
                        listOf(
                            Part.builder()
                                .text("Sistema: Se ofrecieron horas al usuario para $fechaStr ($nombreServicio). Precios informados: Total $precioTotal, Abono $precioAbono.")
                                .build()
                        )
                    )
                    .build()
            )

            AgentResponse.ListOptions(
                text = textoRespuesta,
                buttonLabel = "Ver Horas",
                options = optionsMap
            )
        }.onFailure { logger.warn("Error obteniendo slots para lista interactiva", it) }.getOrNull()
    }

    private fun buscarPrimeraDisponibilidad(servicioStr: String): Pair<LocalDate, List<Instant>>? {
        val servicioDb = buscarServicio(servicioStr)
        val duracion = servicioDb?.duracionMinutos ?: 30
        val maxDiasBusqueda = 30
        val hoy = LocalDate.now(clinicZoneId)

        for (i in 0..maxDiasBusqueda) {
            val fecha = hoy.plusDays(i.toLong())
            val slots = disponibilidadService.obtenerSlots(fecha, duracion)
            if (slots.isNotEmpty()) {
                return fecha to slots
            }
        }
        return null
    }

    private fun ejecutarHerramienta(functionCall: FunctionCall, user: User?, telefono: String): ToolExecutionResult {
        val funcName = functionCall.name().orElse("")
        val argsMap = functionCall.args().orElse(Collections.emptyMap())

        val tool = agentTools.find { it.name == funcName }
        return if (tool != null) {
            tool.execute(argsMap, ToolContext(telefono, user?.id))
        } else {
            ToolExecutionResult("Error: Función desconocida o argumentos inválidos.")
        }
    }

    private fun buscarServicio(input: String): ServicioMedico? {
        val todos = servicioMedicoRepository.findByActivoTrue()
        // 1. Match exacto (case insensitive)
        // 2. Contiene nombre
        // 3. Fallback a "Consulta General" si no hay match
        return todos.find { it.nombre.equals(input, ignoreCase = true) }
            ?: todos.find { it.nombre.contains(input, ignoreCase = true) }
            ?: todos.find { it.nombre.equals("Consulta General", ignoreCase = true) }
    }

    private fun prepararHistorial(telefono: String): MutableList<Content> {
        val historial = chatHistory.computeIfAbsent(telefono) { mutableListOf() }
        if (historial.size > 12) {
            historial.subList(0, historial.size - 12).clear()
        }
        return historial
    }

    private fun buscarUsuarioPorTelefono(telefono: String): User? =
        userRepository.findByPhone(telefono)
            ?: userRepository.findByPhone(telefono.removePrefix("56"))
            ?: userRepository.findByPhone("+" + telefono)

    private fun construirContextoCliente(user: User?): String {
        if (user == null) return "Cliente Nuevo (No registrado)."

        val mascotas = mascotaRepository.findAllByTutorId(user.id!!)
        val resumenMascotas = if (mascotas.isNotEmpty()) {
            mascotas.joinToString(", ") { "${it.nombre} (${it.especie})" }
        } else {
            "sin mascotas registradas aún"
        }
        return "Nombre: ${user.name}. Mascotas registradas: $resumenMascotas."
    }

    private fun construirListadoServicios(): String {
        val serviciosActivos = servicioMedicoRepository.findByActivoTrue()
        return if (serviciosActivos.isNotEmpty()) {
            serviciosActivos.joinToString("\n") { "- ${it.nombre} (${it.duracionMinutos} min)" }
        } else {
            "Consultar en recepción"
        }
    }

    private fun construirSystemInstruction(contextoCliente: String, listaServicios: String, telefono: String): Content {
        val systemPromptText = """
            Eres "CliniBot", el asistente virtual de la veterinaria "Clinipets" ubicada en Temuco (Sector Inés de Suárez).
            Tu tono es cercano, profesional, empático y adaptado a Chile.
            RESPUESTA CORTA: Máximo 2 frases o una lista breve. Sin rodeos ni relleno.

            REGLAS DE ORO (IMPORTANTE):
            - PROHIBIDO pedir el número de teléfono. Ya lo conoces (es el ID de sesión). Si una herramienta falla por falta de datos, pide el dato específico (ej: nombre de mascota), pero nunca el teléfono.
            - NO realizamos atención a domicilio. Solo atendemos en nuestra clínica establecida.
            - NO atendemos urgencias de riesgo vital inmediato. Solo atención agendada. Si es una urgencia grave, sugiere ir a un hospital veterinario 24/7.
            - Si preguntan por "Vacuna Leucemia" (felina), ADVIERTE que requiere un test retroviral negativo previo para poder administrarla.
            - Si preguntan por "Esterilización Canina", PREGUNTA el peso aproximado de la mascota para poder dar un valor exacto (los precios varían entre $30.000 y $54.000 según peso).
            
            Información del cliente: $contextoCliente
            Teléfono Validado (ID Sesión): $telefono
            Fecha de hoy: ${LocalDate.now()}
            
            Servicios Disponibles:
            $listaServicios
            
            🧠 INTELIGENCIA DE CONTEXTO:
            1. Inferencia de Especie/Sexo:
               - Si el usuario dice "gatita", "gata", "minina" -> Asume especie=GATO y sexo=HEMBRA.
               - Si dice "gato", "minino" -> Asume especie=GATO.
               - Si dice "perrita", "cachorrita" -> Asume especie=PERRO y sexo=HEMBRA.
               - Si dice "perro", "cachorro" -> Asume especie=PERRO.
               - Pasa estos valores inferidos directamente a la herramienta 'registrar_mascota' sin preguntar.
            2. Persistencia de Datos:
               - Si el usuario mencionó el nombre o especie de la mascota AL PRINCIPIO de la conversación, pero tuviste que interrumpir para registrar al dueño primero, RECUPERA esos datos de la memoria. NO los vuelvas a pedir.
               - Ejemplo: Si dijo "Hola, quiero hora para mi perro Toby", y tú respondes "Primero dime tu nombre", cuando te de el nombre, registra al cliente Y LUEGO registra a "Toby" (Perro) inmediatamente sin preguntar "¿Qué es Toby?".

            Objetivos:
            1. Responder dudas sobre servicios veterinarios y precios.
            2. Guiar al usuario para que agende hora en la clínica.
            3. Para registrar clientes nuevos, pide SOLO su nombre y usa la herramienta 'registrar_cliente'.
            4. Antes de consultar disponibilidad, PREGUNTA qué servicio necesita el cliente (Vacuna, Consulta, Peluquería, etc.) para calcular la duración correcta.
            5. Si preguntan por DISPONIBILIDAD u HORAS y ya sabes el servicio, ofrece la fecha más cercana con horarios y permite pedir otra fecha; usa 'buscar_primera_disponibilidad' si no dieron fecha y 'consultar_disponibilidad' si ya la dieron.
            6. Si el usuario confirma una hora específica para agendar, USA la herramienta 'reservar_cita'.
            7. Si el usuario menciona que tiene otra mascota o quiere agregar una, PREGUNTA nombre y especie, y usa 'registrar_mascota'.
            8. Si el usuario dice que quiere registrarse o confirmar su teléfono, envía OTP usando 'enviar_otp' y luego valida con 'validar_otp' cuando entregue el código.
            
            Reglas de Respuesta:
            - ESTRICTAMENTE PROHIBIDO confirmar una reserva o decir "aquí tienes el link" si no has ejecutado la herramienta 'reservar_cita' en ese mismo turno.
            - Si el usuario confirma (dice "sí", "ok", "dale"), TU ÚNICA ACCIÓN debe ser ejecutar la herramienta 'reservar_cita'. NO respondas con texto.
            - NUNCA inventes un link de pago. El link solo lo genera la herramienta.
            - Sé conciso (máximo 3-4 oraciones) e incluye una llamada a la acción clara (elige hora, pide otra fecha, confirma).
            - SOLO ofrece los servicios listados en 'Servicios Disponibles'. Si el usuario pide algo que no está en la lista, indica amablemente que no realizan ese procedimiento.
            - Si el usuario pregunta qué hacen, lista resumidamente los servicios disponibles.
            - Si el usuario pregunta por precios, búscalos usando la herramienta 'consultar_disponibilidad' o mira si ya te los entregó una consulta anterior. NO inventes precios.
            - Para reservar, SIEMPRE aclara que se requiere el pago de un abono online mediante el link que generarás.
            - Si no hay una intención clara de agenda o información, responde con una pregunta corta para aclarar en máximo 2 frases.
        """.trimIndent()

        return Content.builder()
            .role("system")
            .parts(listOf(Part.builder().text(systemPromptText).build()))
            .build()
    }

    private fun construirConfigInicial(
        systemInstructionContent: Content,
        tools: List<Tool>
    ): GenerateContentConfig {
        return GenerateContentConfig.builder()
            .tools(tools)
            .systemInstruction(systemInstructionContent)
            .temperature(temperature)
            .maxOutputTokens(maxOutputTokens)
            .build()
    }

    private fun construirUserContent(mensajeUsuario: String): Content =
        Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(mensajeUsuario.trim()).build()))
            .build()

    private fun registrarConversacion(historial: MutableList<Content>, userContent: Content, textoFinal: String) {
        historial.add(userContent)
        historial.add(
            Content.builder()
                .role("model")
                .parts(listOf(Part.builder().text(textoFinal).build()))
                .build()
        )
    }

    fun generarDashboard(usuarioId: UUID): DashboardResponse {
        val usuario = userRepository.findById(usuarioId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }
        val mascotas = mascotaRepository.findAllByTutorId(usuarioId)
        val fichasRecientes = mascotas.associate { mascota ->
            val ficha = mascota.id?.let {
                fichaClinicaRepository.findAllByMascotaIdOrderByFechaAtencionDesc(it).firstOrNull()
            }
            mascota.id!! to ficha
        }
        val serviciosActivos = servicioMedicoRepository.findByActivoTrue()

        val saludo = "Hola ${usuario.name}!"
        val resumenMascotas = if (mascotas.isEmpty()) {
            "El usuario no tiene mascotas registradas."
        } else {
            mascotas.joinToString("\n") { mascota ->
                val ficha = fichasRecientes[mascota.id!!]
                val detalleFicha = ficha?.let {
                    val fecha = it.fechaAtencion.atZone(clinicZoneId).toLocalDate()
                    "Última atención ${fecha}: Diagnóstico ${it.diagnostico ?: "N/A"}, Tratamiento ${it.tratamiento ?: "N/A"}"
                } ?: "Sin atenciones previas."
                "- ${mascota.nombre} (${mascota.especie}). $detalleFicha"
            }
        }

        val listaServiciosPrompt = if (serviciosActivos.isEmpty()) {
            "Sin servicios configurados."
        } else {
            serviciosActivos.joinToString("\n") { "- ${it.id}: ${it.nombre} (${it.categoria})" }
        }

        val iaResult = runCatching {
            generarDashboardConIa(
                nombreUsuario = usuario.name,
                resumenMascotas = resumenMascotas,
                listaServicios = listaServiciosPrompt
            )
        }.getOrNull()

        val mensajeIa = iaResult?.mensaje ?: "¡Bienvenido a Clinipets! Cuida a tu mejor amigo."
        val serviciosDestacados = iaResult?.serviciosIds?.let { ids ->
            serviciosActivos.filter { ids.contains(it.id.toString()) }
        }.orEmpty().ifEmpty { serviciosActivos.take(2) }

        return DashboardResponse(
            saludo = saludo,
            mensajeIa = mensajeIa,
            mascotas = mascotas.map { it.toResponse() },
            serviciosDestacados = serviciosDestacados.map { it.toDto() },
            todosLosServicios = serviciosActivos.map { it.toDto() }
        )
    }

    private fun generarDashboardConIa(
        nombreUsuario: String,
        resumenMascotas: String,
        listaServicios: String
    ): IaDashboardResult? {
        if (!iaEnabled) return null

        val systemPrompt = """
            Eres veterinario de Clinipets (Chile).
            Devuelve SOLO JSON en una línea, sin texto extra ni comillas sobrantes:
            {"mensaje":"<breve y empático>","serviciosIds":["<uuid1>","<uuid2>"]}
            Reglas: tono cercano, 2-3 oraciones máximo; si hubo atención reciente, menciónala; si no, sugiere control/vacunas; serviciosIds debe tener exactamente 2 UUID de la lista dada.
            Si dudas o falta contexto, responde igual en JSON con serviciosIds vacíos.
        """.trimIndent()

        val userPrompt = """
            Usuario: $nombreUsuario
            Mascotas e historial:
            $resumenMascotas

            Lista de servicios disponibles (elige 2 IDs):
            $listaServicios
        """.trimIndent()

        val systemContent = Content.builder()
            .role("system")
            .parts(listOf(Part.builder().text(systemPrompt).build()))
            .build()

        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(userPrompt).build()))
            .build()

        val config = GenerateContentConfig.builder()
            .systemInstruction(systemContent)
            .responseMimeType("application/json")
            .temperature(0.2f)
            .build()

        return try {
            logger.info(
                "[IA_DASHBOARD] Enviando prompt. user={} resumenLen={} serviciosLen={}",
                nombreUsuario,
                resumenMascotas.length,
                listaServicios.length
            )
            val response = client.generateContent(modelName, listOf(userContent), config)
            val rawText = response.text() ?: return null
            logger.info("[IA_DASHBOARD] Respuesta recibida len={} preview='{}'", rawText.length, rawText.take(200))
            try {
                val parsed: Map<String, Any> = mapper.readValue(rawText)
                val mensaje = parsed["mensaje"] as? String ?: return null
                val serviciosIds = (parsed["serviciosIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                IaDashboardResult(mensaje.trim(), serviciosIds)
            } catch (parseEx: Exception) {
                logger.warn("[IA_DASHBOARD] Respuesta no JSON, usando fallback. raw='{}'", rawText.take(200))
                IaDashboardResult(rawText.trim(), emptyList())
            }
        } catch (e: Exception) {
            logger.error("[IA_DASHBOARD] Error generando dashboard para {}", nombreUsuario, e)
            null
        }
    }

    fun generarResumenWhatsapp(
        anamnesis: String,
        diagnostico: String,
        tratamiento: String,
        nombreMascota: String
    ): String {
        val systemPrompt = """
            Eres CliniBot, asistente de Clinipets. Escribe mensajes breves y empáticos para tutores en Chile.
            Objetivo: entregar un cierre de atención apto para WhatsApp con emojis, en 2-3 oraciones, tono cercano y profesional.
            Incluye recordatorio del tratamiento sin tecnicismos excesivos y firma con Clinipets.
            No agregues advertencias legales ni formatos adicionales (solo el texto final listo para enviar).
        """.trimIndent()

        val userPrompt = """
            Datos clínicos:
            - Mascota: $nombreMascota
            - Anamnesis: $anamnesis
            - Diagnóstico: $diagnostico
            - Tratamiento: $tratamiento

            Redacta un único mensaje listo para WhatsApp para el tutor. Sé breve (2-3 oraciones) y empático; incluye emojis.
        """.trimIndent()

        val systemContent = Content.builder()
            .role("system")
            .parts(listOf(Part.builder().text(systemPrompt).build()))
            .build()

        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(userPrompt).build()))
            .build()

        val config = GenerateContentConfig.builder()
            .systemInstruction(systemContent)
            .temperature(0.3f)
            .build()

        return try {
            val response = client.generateContent(modelName, listOf(userContent), config)
            response.text()?.trim()
                ?: "Hola! 🐾 ${nombreMascota} ya está ok. Le dejamos: $tratamiento. Si tienes dudas, escríbenos. Clinipets"
        } catch (e: Exception) {
            logger.error("[IA_RESUMEN] Error generando resumen para {}", nombreMascota, e)
            "Hola! 🐾 ${nombreMascota} ya está ok. Le dejamos: $tratamiento. Si tienes dudas, escríbenos. Clinipets"
        }
    }

    fun esNombreInapropiado(texto: String): Boolean {
        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text("Analiza si es ofensivo: $texto").build()))
            .build()

        val config = GenerateContentConfig.builder()
            .temperature(0.0f)
            .responseMimeType("application/json")
            .build()

        return try {
            val response = client.generateContent(modelName, userContent, config)
            val rawText = response.text() ?: "{}"
            rawText.contains("\"inapropiado\": true")
        } catch (e: Exception) {
            false
        }
    }
}

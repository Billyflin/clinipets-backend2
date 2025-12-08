package cl.clinipets.core.ia

import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.Sexo
import com.google.genai.types.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

sealed class AgentResponse {
    data class Text(val content: String, val paymentUrl: String? = null) : AgentResponse()
    data class ListOptions(
        val text: String,
        val buttonLabel: String,
        val options: Map<String, String>
    ) : AgentResponse()
}

@Service
class VeterinaryAgentService(
    private val userRepository: UserRepository,
    private val mascotaRepository: MascotaRepository,
    private val disponibilidadService: cl.clinipets.agendamiento.application.DisponibilidadService,
    private val reservaService: ReservaService,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val clinicZoneId: ZoneId,
    private val otpService: cl.clinipets.identity.application.OtpService,
    private val client: GenAiClientWrapper,
    @Value("\${gemini.model:gemini-2.0-flash-lite}") private val modelName: String
) {
    private val logger = LoggerFactory.getLogger(VeterinaryAgentService::class.java)
    private val chatHistory = ConcurrentHashMap<String, MutableList<Content>>()

    init {
        logger.info("[IA_INIT] Servicio Agente Veterinario listo. Modelo: $modelName")
    }

    fun procesarMensaje(telefono: String, mensajeUsuario: String): AgentResponse {
        logger.info("[IA_AGENT] Procesando mensaje para teléfono: $telefono")

        val historial = prepararHistorial(telefono)
        val user = buscarUsuarioPorTelefono(telefono)
        val contextoCliente = construirContextoCliente(user)
        val listaServicios = construirListadoServicios()
        val systemInstructionContent = construirSystemInstruction(contextoCliente, listaServicios)
        val tools = crearHerramientas()
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
                systemInstructionContent = systemInstructionContent
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
        systemInstructionContent: Content
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
                systemInstructionContent = systemInstructionContent
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
        systemInstructionContent: Content
    ): AgentResponse {
        val funcName = functionCall.name().orElse("unknown")
        logger.info("[IA_AGENT] La IA solicita ejecutar herramienta: $funcName")

        intentarRespuestaConLista(functionCall, userContent, historial)?.let { return it }

        val toolResult = ejecutarHerramienta(functionCall, user, telefono)
        logger.warn("[DEBUG_PAGO] ToolResult recibido en procesarMensaje. URL: {}", toolResult.paymentUrl)

        val toolContent = Content.builder()
            .role("function")
            .parts(listOf(toolResult.part))
            .build()

        val historialConTool = ArrayList(mensajesParaEnviar)
        modelContent?.let { historialConTool.add(it) }
        historialConTool.add(toolContent)

        val finalConfig = GenerateContentConfig.builder()
            .systemInstruction(systemInstructionContent)
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
        val argsMap = functionCall.args().orElse(Collections.emptyMap())
        val funcName = functionCall.name().orElse("")

        var paymentUrl: String? = null

        val resultText = when (funcName) {
            "registrar_cliente" -> registrarCliente(argsMap, telefono)
            "consultar_disponibilidad" -> consultarDisponibilidad(argsMap)
            "buscar_primera_disponibilidad" -> buscarPrimeraDisponibilidadTexto(argsMap)
            "enviar_otp" -> {
                otpService.requestOtp(telefono)
                "Te envié un código de verificación a este número. Respóndeme con ese código de 6 dígitos."
            }

            "validar_otp" -> {
                val code = argsMap["code"] as? String ?: "Error: Código requerido."
                if (code.startsWith("Error")) {
                    code
                } else {
                    otpService.validateOtp(telefono, code)
                    marcarTelefonoVerificado(telefono)
                    "Código verificado. Quedaste autenticado con tu teléfono."
                }
            }

            "registrar_mascota" -> registrarMascota(argsMap, user, telefono)
            "reservar_cita" -> reservarCita(argsMap, user, telefono).also { paymentUrl = it.second }.first
            else -> "Error: Función desconocida o argumentos inválidos."
        }

        val part = Part.builder()
            .functionResponse(
                FunctionResponse.builder()
                    .name(funcName)
                    .response(mapOf("result" to resultText))
                    .build()
            )
            .build()

        logger.warn("[DEBUG_PAGO] Retornando ToolExecutionResult. URL: {}", paymentUrl)
        return ToolExecutionResult(part = part, paymentUrl = paymentUrl)
    }

    private fun registrarCliente(argsMap: Map<String, Any>, telefono: String): String {
        var nombre = argsMap["nombre"] as? String
        if (nombre == null) return "Error: Nombre requerido."

        nombre = nombre.replace(Regex("(?i)^(hola|soy|me llamo|mi nombre es)\\s+"), "").trim()
        return try {
            val phoneClean = telefono.replace("+", "")
            val emailGen = "wsp_$phoneClean@clinipets.local"

            if (userRepository.existsByEmailIgnoreCase(emailGen)) {
                "El usuario ya estaba registrado."
            } else {
                val newUser = User(
                    name = nombre,
                    email = emailGen,
                    phone = telefono,
                    passwordHash = "wsp_auto_generated",
                    role = UserRole.CLIENT
                )
                userRepository.save(newUser)
                "¡Registrada como $nombre! Ahora puedes agendar."
            }
        } catch (e: Exception) {
            logger.error("Error registrando cliente", e)
            "Error al registrar cliente."
        }
    }

    private fun consultarDisponibilidad(argsMap: Map<String, Any>): String {
        val fechaStr = argsMap["fecha"] as? String ?: return "Error: Fecha requerida."
        val servicioStr = argsMap["servicio"] as? String

        return try {
            val fecha = LocalDate.parse(fechaStr)

            val servicioDb = servicioStr?.let { buscarServicio(it) }
            val duracion = servicioDb?.duracionMinutos ?: 30
            val nombreServicio = servicioDb?.nombre ?: "Consulta General"
            val precioTotal = servicioDb?.precioBase ?: 0
            val precioAbono = servicioDb?.precioAbono ?: 0

            val slots = disponibilidadService.obtenerSlots(fecha, duracion)
            if (slots.isEmpty()) {
                "No quedan horas disponibles para $nombreServicio el $fechaStr."
            } else {
                "Encontré ${slots.size} horas para $nombreServicio (Duración: $duracion min). Valor Total: $$precioTotal. REGLA DE PAGO: Para agendar, el cliente DEBE pagar un abono online de $$precioAbono ahora mismo. El saldo se paga en clínica."
            }
        } catch (e: Exception) {
            "Fecha inválida o error en agenda."
        }
    }

    private fun buscarPrimeraDisponibilidadTexto(argsMap: Map<String, Any>): String {
        val servicioStr = argsMap["servicio"] as? String ?: return "Error: Servicio requerido."
        val primera = buscarPrimeraDisponibilidad(servicioStr) ?: return "No encontré horas cercanas para ese servicio."
        val servicioDb = buscarServicio(servicioStr)
        val nombreServicio = servicioDb?.nombre ?: servicioStr
        val duracion = servicioDb?.duracionMinutos ?: 30
        return "Tengo la fecha más cercana para $nombreServicio (duración $duracion min) el ${primera.first}. ¿Quieres ver los horarios?"
    }

    private fun registrarMascota(argsMap: Map<String, Any>, user: User?, telefono: String): String {
        val nombre = (argsMap["nombre"] as? String)?.trim()
        val especieInput = (argsMap["especie"] as? String)?.trim()
        val raza = (argsMap["raza"] as? String)?.trim()

        val userDb = resolverTutor(user, telefono) ?: return "Error: No registrado o no identificado."
        if (nombre.isNullOrBlank() || especieInput.isNullOrBlank()) return "Error: Nombre y especie son obligatorios."

        return try {
            val especie =
                if (especieInput.lowercase().contains("gato") || especieInput.lowercase().contains("felino")) {
                    Especie.GATO
                } else {
                    Especie.PERRO
                }

            val nuevaMascota = Mascota(
                nombre = nombre,
                especie = especie,
                pesoActual = BigDecimal("10.0"),
                raza = raza?.takeIf { it.isNotBlank() } ?: "Mestizo",
                sexo = Sexo.MACHO,
                fechaNacimiento = LocalDate.now(),
                tutor = userDb
            )
            mascotaRepository.save(nuevaMascota)
            "¡Listo! Agregué a $nombre ($especie) a tu perfil. ¿Quieres agendar hora para él/ella?"
        } catch (e: Exception) {
            logger.error("Error registrando mascota", e)
            "Error al registrar la mascota."
        }
    }

    private fun reservarCita(argsMap: Map<String, Any>, user: User?, telefono: String): Pair<String, String?> {
        val fechaHoraStr = argsMap["fechaHora"] as? String
        val servicioStr = argsMap["servicio"] as? String
        val userDb = resolverTutor(user, telefono) ?: return "Error: No registrado o no identificado." to null

        if (fechaHoraStr == null) return "Error: Fecha y hora requerida." to null

        return try {
            val fechaHora = LocalDateTime.parse(fechaHoraStr)
            val fechaHoraInstant = fechaHora.atZone(clinicZoneId).toInstant()

            val mascotas = mascotaRepository.findAllByTutorId(userDb.id!!)
            if (mascotas.isEmpty()) {
                return "Error: No tienes mascotas registradas. Debes registrar una mascota antes de agendar." to null
            }

            val servicioDb = servicioStr?.let { buscarServicio(it) }
            if (servicioDb == null) {
                return "Error: No se encontró el servicio médico solicitado." to null
            }

            val mascota = mascotas.first()
            val detalles = listOf(DetalleReservaRequest(servicioId = servicioDb.id!!, mascotaId = mascota.id))
            val tutorPayload = JwtPayload(
                userId = userDb.id!!,
                email = userDb.email,
                role = userDb.role,
                expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            )

            val resultado = reservaService.crearReserva(
                detallesRequest = detalles,
                fechaHoraInicio = fechaHoraInstant,
                origen = OrigenCita.WHATSAPP,
                tutor = tutorPayload
            )
            logger.warn("[DEBUG_PAGO] Reserva creada. Resultado PaymentURL: {}", resultado.paymentUrl)
            "EXITO: Reserva creada. Dile al usuario que le enviaremos el link de pago en un mensaje separado." to resultado.paymentUrl
        } catch (e: Exception) {
            logger.error("Error creando reserva", e)
            "Ocurrió un error al intentar crear la reserva: ${e.message}" to null
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

    private fun crearHerramientas(): List<Tool> {
        val propString = Schema.builder()
            .type(com.google.genai.types.Type.Known.STRING)
            .build()

        val funcRegistrar = FunctionDeclaration.builder()
            .name("registrar_cliente")
            .description("Registra un nuevo cliente solo con su nombre.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(mapOf("nombre" to propString))
                    .required(listOf("nombre"))
                    .build()
            )
            .build()

        val funcConsultar = FunctionDeclaration.builder()
            .name("consultar_disponibilidad")
            .description("Consulta disponibilidad. Requiere fecha y tipo de servicio.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(mapOf("fecha" to propString, "servicio" to propString))
                    .required(listOf("fecha", "servicio"))
                    .build()
            )
            .build()

        val funcBuscarPrimera = FunctionDeclaration.builder()
            .name("buscar_primera_disponibilidad")
            .description("Busca la primera fecha con horas disponibles para un servicio.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(mapOf("servicio" to propString))
                    .required(listOf("servicio"))
                    .build()
            )
            .build()

        val funcReservar = FunctionDeclaration.builder()
            .name("reservar_cita")
            .description("Confirma reserva. Requiere fechaHora ISO y servicio.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(mapOf("fechaHora" to propString, "servicio" to propString))
                    .required(listOf("fechaHora", "servicio"))
                    .build()
            )
            .build()

        val funcRegistrarMascota = FunctionDeclaration.builder()
            .name("registrar_mascota")
            .description("Registra una nueva mascota para el usuario actual.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(
                        mapOf(
                            "nombre" to propString,
                            "especie" to propString,
                            "raza" to propString
                        )
                    )
                    .required(listOf("nombre", "especie"))
                    .build()
            )
            .build()

        val funcEnviarOtp = FunctionDeclaration.builder()
            .name("enviar_otp")
            .description("Envía un código OTP al teléfono del chat actual.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .build()
            )
            .build()

        val funcValidarOtp = FunctionDeclaration.builder()
            .name("validar_otp")
            .description("Valida el código OTP enviado al teléfono.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(mapOf("code" to propString))
                    .required(listOf("code"))
                    .build()
            )
            .build()

        return listOf(
            Tool.builder()
                .functionDeclarations(
                    listOf(
                        funcRegistrar,
                        funcConsultar,
                        funcBuscarPrimera,
                        funcEnviarOtp,
                        funcValidarOtp,
                        funcReservar,
                        funcRegistrarMascota
                    )
                )
                .build()
        )
    }

    private fun prepararHistorial(telefono: String): MutableList<Content> {
        val historial = chatHistory.computeIfAbsent(telefono) { mutableListOf() }
        if (historial.size > 20) {
            historial.subList(0, historial.size - 10).clear()
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

    private fun construirSystemInstruction(contextoCliente: String, listaServicios: String): Content {
        val systemPromptText = """
            Eres "CliniBot", el asistente virtual de la veterinaria "Clinipets". 
            Tu tono es cercano, profesional, empático y adaptado a Chile.
            
            Información del cliente: $contextoCliente
            Fecha de hoy: ${LocalDate.now()}
            
            Servicios Disponibles:
            $listaServicios
            
            Objetivos:
            1. Responder dudas sobre servicios veterinarios.
            2. Guiar al usuario para que agende hora.
            3. Para registrar clientes nuevos, pide SOLO su nombre y usa la herramienta 'registrar_cliente'.
            4. Antes de consultar disponibilidad, PREGUNTA qué servicio necesita el cliente (Vacuna, Consulta, Peluquería, etc.) para calcular la duración.
            5. Si preguntan por DISPONIBILIDAD u HORAS y ya sabes el servicio, ofrece la fecha más cercana con horarios y permite pedir otra fecha; usa 'buscar_primera_disponibilidad' si no dieron fecha y 'consultar_disponibilidad' si ya la dieron.
            6. Si el usuario confirma una hora específica para agendar, USA la herramienta 'reservar_cita'.
            7. Si el usuario menciona que tiene otra mascota o quiere agregar una, PREGUNTA nombre y especie, y usa 'registrar_mascota'.
            8. Si el usuario dice que quiere registrarse o confirmar su teléfono, envía OTP usando 'enviar_otp' y luego valida con 'validar_otp' cuando entregue el código.
            
            Reglas:
            - ESTRICTAMENTE PROHIBIDO confirmar una reserva o decir "aquí tienes el link" si no has ejecutado la herramienta 'reservar_cita' en ese mismo turno.
            - Si el usuario confirma (dice "sí", "ok", "dale"), TU ÚNICA ACCIÓN debe ser ejecutar la herramienta 'reservar_cita'. NO respondas con texto.
            - NUNCA inventes un link de pago. El link solo lo genera la herramienta.
            - Sé conciso (máximo 3-4 oraciones) e incluye una llamada a la acción clara (elige hora, pide otra fecha, confirma).
            - SOLO ofrece los servicios listados en 'Servicios Disponibles'. Si el usuario pide algo que no está en la lista, indica amablemente que no realizan ese procedimiento.
            - Si el usuario pregunta qué hacen, lista resumidamente los servicios disponibles.
            - Si el usuario pregunta por precios, búscalos usando la herramienta 'consultar_disponibilidad' o mira si ya te los entregó una consulta anterior. NO inventes precios.
            - Para reservar, SIEMPRE aclara que se requiere el pago de un abono online mediante el link que generarás.
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
            .temperature(0.2f)
            .build()
    }

    private fun construirUserContent(mensajeUsuario: String): Content =
        Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(mensajeUsuario).build()))
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

    private fun resolverTutor(user: User?, telefono: String): User? {
        if (user != null) return user
        return userRepository.findByPhone(telefono)
            ?: userRepository.findByPhone(telefono.removePrefix("56"))
            ?: userRepository.findByPhone("+" + telefono)
    }

    private fun marcarTelefonoVerificado(telefono: String) {
        val normalized = otpService.normalizePhone(telefono)
        userRepository.findByPhone(normalized)?.let {
            it.phoneVerified = true
            userRepository.save(it)
        }
    }

    private data class ToolExecutionResult(val part: Part, val paymentUrl: String? = null)

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

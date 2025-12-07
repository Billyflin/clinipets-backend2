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
import com.google.genai.types.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
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

        val historial = chatHistory.computeIfAbsent(telefono) { mutableListOf() }
        if (historial.size > 20) {
            val sublist = historial.subList(0, historial.size - 10)
            sublist.clear()
        }

        val user = userRepository.findByPhone(telefono)
            ?: userRepository.findByPhone(telefono.removePrefix("56"))
            ?: userRepository.findByPhone("+" + telefono)

        val contextoCliente = if (user != null) {
            val mascotas = mascotaRepository.findAllByTutorId(user.id!!)
            val resumenMascotas = if (mascotas.isNotEmpty()) {
                mascotas.joinToString(", ") { "${it.nombre} (${it.especie})" }
            } else {
                "sin mascotas registradas aún"
            }
            "Nombre: ${user.name}. Mascotas registradas: $resumenMascotas."
        } else {
            "Cliente Nuevo (No registrado)."
        }

        // Recuperar servicios activos para contexto
        val serviciosActivos = servicioMedicoRepository.findByActivoTrue()
        val listaServicios = if (serviciosActivos.isNotEmpty()) {
            serviciosActivos.joinToString("\n") { "- ${it.nombre} (${it.duracionMinutos} min)" }
        } else {
            "Consultar en recepción"
        }

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
            5. Si preguntan por DISPONIBILIDAD u HORAS para una fecha específica y ya sabes el servicio, USA la herramienta 'consultar_disponibilidad'.
            6. Si el usuario confirma una hora específica para agendar, USA la herramienta 'reservar_cita'.
            
            Reglas:
            - ESTRICTAMENTE PROHIBIDO confirmar una reserva o decir "aquí tienes el link" si no has ejecutado la herramienta 'reservar_cita' en ese mismo turno.
            - Si el usuario confirma (dice "sí", "ok", "dale"), TU ÚNICA ACCIÓN debe ser ejecutar la herramienta 'reservar_cita'. NO respondas con texto.
            - NUNCA inventes un link de pago. El link solo lo genera la herramienta.
            - Sé conciso (máximo 3-4 oraciones).
            - SOLO ofrece los servicios listados en 'Servicios Disponibles'. Si el usuario pide algo que no está en la lista, indica amablemente que no realizan ese procedimiento.
            - Si el usuario pregunta qué hacen, lista resumidamente los servicios disponibles.
            - Si el usuario pregunta por precios, búscalos usando la herramienta 'consultar_disponibilidad' o mira si ya te los entregó una consulta anterior. NO inventes precios.
            - Para reservar, SIEMPRE aclara que se requiere el pago de un abono online mediante el link que generarás.
        """.trimIndent()

        val tools = crearHerramientas()

        val systemInstructionContent = Content.builder()
            .role("system")
            .parts(listOf(Part.builder().text(systemPromptText).build()))
            .build()

        val config = GenerateContentConfig.builder()
            .tools(tools)
            .systemInstruction(systemInstructionContent)
            .temperature(0.2f)
            .build()

        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(mensajeUsuario).build()))
            .build()

        val mensajesParaEnviar = ArrayList(historial)
        mensajesParaEnviar.add(userContent)

        return try {
            val response1 = client.generateContent(modelName, mensajesParaEnviar, config)

            val candidates = response1.candidates().orElse(Collections.emptyList())
            val candidate = candidates.firstOrNull()
            val modelContent = candidate?.content()?.orElse(null)
            val parts = modelContent?.parts()?.orElse(Collections.emptyList()) ?: emptyList()

            val functionCallPart = parts.firstOrNull { it.functionCall().isPresent }
            val functionCall = functionCallPart?.functionCall()?.orElse(null)

            val agentResponse: AgentResponse

            if (functionCall != null) {
                val funcName = functionCall.name().orElse("unknown")
                logger.info("[IA_AGENT] La IA solicita ejecutar herramienta: $funcName")

                if (funcName == "consultar_disponibilidad") {
                    val argsMap = functionCall.args().orElse(Collections.emptyMap())
                    val fechaStr = argsMap["fecha"] as? String
                    val servicioStr = argsMap["servicio"] as? String
                    
                    var listOptionsResponse: AgentResponse.ListOptions? = null

                    if (fechaStr != null && servicioStr != null) {
                        try {
                            val fecha = LocalDate.parse(fechaStr)
                            // Buscar servicio real y su duración
                            val servicioDb = buscarServicio(servicioStr)
                            val duracion = servicioDb?.duracionMinutos ?: 30
                            val nombreServicio = servicioDb?.nombre ?: "Consulta General"
                            val precioTotal = servicioDb?.precioBase ?: 0
                            val precioAbono = servicioDb?.precioAbono ?: 0

                            val slots = disponibilidadService.obtenerSlots(fecha, duracion)
                            
                            if (slots.isNotEmpty()) {
                                val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(clinicZoneId)
                                val optionsMap = slots.associate { slot ->
                                    val horaStr = formatter.format(slot)
                                    val id = "RES_${fechaStr}_$horaStr" 
                                    id to horaStr
                                }

                                val textoRespuesta =
                                    "Encontré horas para $nombreServicio ($duracion min) el $fechaStr.\n" +
                                            "Valor Total: $precioTotal (Abono online: $precioAbono).\n" +
                                            "Selecciona una hora:"

                                listOptionsResponse = AgentResponse.ListOptions(
                                    text = textoRespuesta,
                                    buttonLabel = "Ver Horas",
                                    options = optionsMap
                                )

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
                            }
                        } catch (e: Exception) {
                            logger.warn("Error obteniendo slots para lista interactiva", e)
                        }
                    }

                    if (listOptionsResponse != null) {
                        return listOptionsResponse
                    }
                }

                // Fallback: Flujo estándar (Texto)
                val toolResult = ejecutarHerramienta(functionCall, user, telefono)
                logger.warn("[DEBUG_PAGO] ToolResult recibido en procesarMensaje. URL: {}", toolResult.paymentUrl)
                val toolContent = Content.builder()
                    .role("function")
                    .parts(listOf(toolResult.part))
                    .build()

                val historialConTool = ArrayList(mensajesParaEnviar)
                historialConTool.add(modelContent!!)
                historialConTool.add(toolContent)

                val finalConfig = GenerateContentConfig.builder()
                    .systemInstruction(systemInstructionContent)
                    .build()

                val response2 = client.generateContent(modelName, historialConTool, finalConfig)
                val textoFinal = response2.text() ?: "No pude completar la acción."

                logger.warn("[DEBUG_PAGO] Construyendo AgentResponse.Text. URL a inyectar: {}", toolResult.paymentUrl)
                agentResponse = AgentResponse.Text(textoFinal, paymentUrl = toolResult.paymentUrl)
                
                historial.add(userContent)
                historial.add(
                    Content.builder().role("model").parts(listOf(Part.builder().text(textoFinal).build())).build()
                )

            } else {
                val textoFinal = response1.text() ?: "Lo siento, no pude entenderte."
                agentResponse = AgentResponse.Text(textoFinal)

                historial.add(userContent)
                historial.add(
                    Content.builder().role("model").parts(listOf(Part.builder().text(textoFinal).build())).build()
                )
            }

            agentResponse

        } catch (e: Exception) {
            logger.error("[IA_AGENT] Error al procesar mensaje", e)
            AgentResponse.Text("Disculpa, estoy teniendo un problema técnico momentáneo.")
        }
    }

    private fun ejecutarHerramienta(functionCall: FunctionCall, user: User?, telefono: String): ToolExecutionResult {
        val argsMap = functionCall.args().orElse(Collections.emptyMap())
        val funcName = functionCall.name().orElse("")

        var paymentUrl: String? = null

        val resultText = when (funcName) {
            "registrar_cliente" -> {
                var nombre = argsMap["nombre"] as? String
                if (nombre != null) {
                    // Limpieza básica de nombre
                    nombre = nombre.replace(Regex("(?i)^(hola|soy|me llamo|mi nombre es)\\s+"), "").trim()
                    try {
                        // Generar email dummy: wsp_56912345678@clinipets.local
                        val phoneClean = telefono.replace("+", "")
                        val emailGen = "wsp_$phoneClean@clinipets.local"

                        // Validar si ya existe (aunque el flujo debería prevenirlo)
                        if (userRepository.existsByEmailIgnoreCase(emailGen)) {
                            "El usuario ya estaba registrado."
                        } else {
                            val newUser = User(
                                name = nombre,
                                email = emailGen,
                                phone = telefono,
                                passwordHash = "wsp_auto_generated", // Dummy hash
                                role = UserRole.CLIENT
                            )
                            userRepository.save(newUser)
                            "¡Registrada como $nombre! Ahora puedes agendar."
                        }
                    } catch (e: Exception) {
                        logger.error("Error registrando cliente", e)
                        "Error al registrar cliente."
                    }
                } else "Error: Nombre requerido."
            }
            "consultar_disponibilidad" -> {
                val fechaStr = argsMap["fecha"] as? String
                val servicioStr = argsMap["servicio"] as? String
                
                if (fechaStr != null) {
                    try {
                        val fecha = LocalDate.parse(fechaStr)

                        val servicioDb = if (servicioStr != null) buscarServicio(servicioStr) else null
                        val duracion = servicioDb?.duracionMinutos ?: 30
                        val nombreServicio = servicioDb?.nombre ?: "Consulta General"
                        val precioTotal = servicioDb?.precioBase ?: 0
                        val precioAbono = servicioDb?.precioAbono ?: 0

                        val slots = disponibilidadService.obtenerSlots(fecha, duracion)
                        if (slots.isEmpty()) "No quedan horas disponibles para $nombreServicio el $fechaStr."
                        else "Encontré ${slots.size} horas para $nombreServicio (Duración: $duracion min). Valor Total: $$precioTotal. REGLA DE PAGO: Para agendar, el cliente DEBE pagar un abono online de $$precioAbono ahora mismo. El saldo se paga en clínica."
                    } catch (e: Exception) {
                        "Fecha inválida o error en agenda."
                    }
                } else "Error: Fecha requerida."
            }
            "reservar_cita" -> {
                val fechaHoraStr = argsMap["fechaHora"] as? String
                val servicioStr = argsMap["servicio"] as? String
                
                var userDb = user
                if (userDb == null) {
                    userDb = userRepository.findByPhone(telefono)
                        ?: userRepository.findByPhone(telefono.removePrefix("56"))
                                ?: userRepository.findByPhone("+" + telefono)
                }

                if (fechaHoraStr != null && userDb != null) {
                    try {
                        val fechaHora = LocalDateTime.parse(fechaHoraStr)
                        val fechaHoraInstant = fechaHora.atZone(clinicZoneId).toInstant()

                        val mascotas = mascotaRepository.findAllByTutorId(userDb.id!!)
                        if (mascotas.isEmpty()) {
                            "Error: No tienes mascotas registradas. Debes registrar una mascota antes de agendar."
                        } else {
                            val mascota = mascotas.first()

                            // Buscar servicio específico
                            val servicioDb = if (servicioStr != null) buscarServicio(servicioStr) else null

                            if (servicioDb == null) {
                                "Error: No se encontró el servicio médico solicitado."
                            } else {
                                val detalles =
                                    listOf(DetalleReservaRequest(servicioId = servicioDb.id!!, mascotaId = mascota.id))
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
                                logger.warn(
                                    "[DEBUG_PAGO] Reserva creada. Resultado PaymentURL: {}",
                                    resultado.paymentUrl
                                )
                                paymentUrl = resultado.paymentUrl
                                "EXITO: Reserva creada. Dile al usuario que le enviaremos el link de pago en un mensaje separado."
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error creando reserva", e)
                        "Ocurrió un error al intentar crear la reserva: ${e.message}"
                    }
                } else {
                    if (userDb == null) "Error: No registrado o no identificado."
                    else "Error: Fecha y hora requerida."
                }
            }
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

        return listOf(
            Tool.builder()
                .functionDeclarations(listOf(funcRegistrar, funcConsultar, funcReservar))
                .build()
        )
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

package cl.clinipets.core.ia

import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
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
    data class Text(val content: String) : AgentResponse()
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

        // 1. Gestión de Memoria
        val historial = chatHistory.computeIfAbsent(telefono) { mutableListOf() }
        if (historial.size > 20) {
            val sublist = historial.subList(0, historial.size - 10)
            sublist.clear()
        }

        // 2. Contexto Usuario
        val user = userRepository.findByPhone(telefono)
            ?: userRepository.findByPhone(telefono.removePrefix("56"))
            ?: userRepository.findByPhone("+" + telefono)

        logger.info(
            "[DIAGNOSTICO] Usuario BD encontrado: {}",
            if (user != null) "SI (ID: ${user.id})" else "NO (Cliente Nuevo)"
        )

        val contextoCliente = if (user != null) {
            val mascotas = mascotaRepository.findAllByTutorId(user.id!!)
            val resumenMascotas = if (mascotas.isNotEmpty()) {
                mascotas.joinToString(", ") { "${it.nombre} (${it.especie})" }
            } else {
                "sin mascotas registradas aún"
            }
            "Nombre: ${user.name}. Mascotas registradas: $resumenMascotas."
        } else {
            "Cliente Nuevo."
        }

        // 3. System Prompt
        val systemPromptText = """
            Eres "CliniBot", el asistente virtual de la veterinaria "Clinipets". 
            Tu tono es cercano, profesional, empático y adaptado a Chile.
            
            Información del cliente: $contextoCliente
            Fecha de hoy: ${LocalDate.now()}
            
            Objetivos:
            1. Responder dudas sobre servicios veterinarios.
            2. Guiar al usuario para que agende hora.
            3. Si preguntan por DISPONIBILIDAD u HORAS para una fecha específica, USA la herramienta 'consultar_disponibilidad'.
            4. Si el usuario confirma una hora específica para agendar, USA la herramienta 'reservar_cita'.
            
            Reglas:
            - Sé conciso (máximo 3-4 oraciones).
        """.trimIndent()

        logger.info("[DIAGNOSTICO] System Prompt generado: \n$systemPromptText")

        val tools = crearHerramientas()

        val systemInstructionContent = Content.builder()
            .role("system")
            .parts(listOf(Part.builder().text(systemPromptText).build()))
            .build()

        val config = GenerateContentConfig.builder()
            .tools(tools)
            .systemInstruction(systemInstructionContent)
            .temperature(0.5f)
            .build()

        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(mensajeUsuario).build()))
            .build()

        val mensajesParaEnviar = ArrayList(historial)
        mensajesParaEnviar.add(userContent)

        return try {
            // --- PRIMERA LLAMADA (Turno 1) ---
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

                    var listOptionsResponse: AgentResponse.ListOptions? = null

                    if (fechaStr != null) {
                        try {
                            val fecha = LocalDate.parse(fechaStr)
                            val slots = disponibilidadService.obtenerSlots(fecha, 30)

                            if (slots.isNotEmpty()) {
                                val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(clinicZoneId)
                                val optionsMap = slots.associate { slot ->
                                    val horaStr = formatter.format(slot)
                                    // ID único para procesar selección después
                                    val id = "RES_${fechaStr}_$horaStr"
                                    id to horaStr
                                }

                                listOptionsResponse = AgentResponse.ListOptions(
                                    text = "He encontrado estas horas disponibles para el $fechaStr:",
                                    buttonLabel = "Ver Horas",
                                    options = optionsMap
                                )

                                // MEMORIA DE SISTEMA (Nota interna)
                                historial.add(userContent)
                                historial.add(
                                    Content.builder()
                                        .role("model")
                                        .parts(
                                            listOf(
                                                Part.builder()
                                                    .text("Sistema: Se ofrecieron horas al usuario para $fechaStr.")
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

                // Fallback: Flujo estándar (Texto) para 'reservar_cita' o errores de disponibilidad
                // Aquí pasamos el teléfono también para que ejecutarHerramienta pueda re-buscar si es necesario o usarlo en logs
                val functionResponsePart = ejecutarHerramienta(functionCall, user, telefono)
                val toolContent = Content.builder()
                    .role("function")
                    .parts(listOf(functionResponsePart))
                    .build()

                val historialConTool = ArrayList(mensajesParaEnviar)
                historialConTool.add(modelContent!!)
                historialConTool.add(toolContent)

                val finalConfig = GenerateContentConfig.builder()
                    .systemInstruction(systemInstructionContent)
                    .build()

                val response2 = client.generateContent(modelName, historialConTool, finalConfig)
                val textoFinal = response2.text() ?: "No pude completar la acción."

                agentResponse = AgentResponse.Text(textoFinal)

                // Guardar en memoria
                historial.add(userContent)
                historial.add(
                    Content.builder().role("model").parts(listOf(Part.builder().text(textoFinal).build())).build()
                )

            } else {
                // Respuesta directa (Texto)
                val textoFinal = response1.text() ?: "Lo siento, no pude entenderte."
                agentResponse = AgentResponse.Text(textoFinal)

                // Guardar en memoria
                historial.add(userContent)
                historial.add(
                    Content.builder().role("model").parts(listOf(Part.builder().text(textoFinal).build())).build()
                )
            }

            agentResponse

        } catch (e: Exception) {
            logger.error("[CRITICAL_FAILURE] Excepción en flujo IA", e)
            AgentResponse.Text("Disculpa, estoy teniendo un problema técnico momentáneo.")
        }
    }

    private fun ejecutarHerramienta(functionCall: FunctionCall, user: User?, telefono: String): Part {
        val argsMap = functionCall.args().orElse(Collections.emptyMap())
        val funcName = functionCall.name().orElse("")

        logger.info("[DIAGNOSTICO] Ejecutando tool '{}' con args: {}", funcName, argsMap)

        val resultText = when (funcName) {
            "consultar_disponibilidad" -> {
                val fechaStr = argsMap["fecha"] as? String
                if (fechaStr != null) {
                    try {
                        val fecha = LocalDate.parse(fechaStr)
                        val slots = disponibilidadService.obtenerSlots(fecha, 30)
                        if (slots.isEmpty()) "No quedan horas disponibles para el $fechaStr."
                        else "Se encontraron ${slots.size} horas. (Se deberían haber mostrado en lista)."
                    } catch (e: Exception) {
                        "Fecha inválida o error en agenda."
                    }
                } else "Error: Fecha requerida."
            }

            "reservar_cita" -> {
                val fechaHoraStr = argsMap["fechaHora"] as? String

                // Re-validación de usuario por si 'user' venía nulo pero existe en BD (caso borde)
                var userDb = user
                if (userDb == null) {
                    userDb = userRepository.findByPhone(telefono)
                        ?: userRepository.findByPhone(telefono.removePrefix("56"))
                                ?: userRepository.findByPhone("+" + telefono)
                }

                logger.info("[DIAGNOSTICO_TOOL] Buscando usuario para reservar. Telefono: $telefono, Encontrado: ${userDb != null}")

                if (userDb == null) {
                    logger.warn("[DIAGNOSTICO_FAIL] ¡INTENTO DE RESERVA SIN USUARIO EN BD! El agente cree que registró al usuario pero no existe en Postgres.")
                }

                if (fechaHoraStr != null && userDb != null) {
                    try {
                        // 1. Parsear fecha
                        // fechaHoraStr vendrá probablemente como '2025-12-08T10:00'
                        val fechaHora = LocalDateTime.parse(fechaHoraStr)
                        val fechaHoraInstant = fechaHora.atZone(clinicZoneId).toInstant()

                        // 2. Validar Mascotas
                        val mascotas = mascotaRepository.findAllByTutorId(userDb.id!!)
                        if (mascotas.isEmpty()) {
                            "Error: No tienes mascotas registradas. Debes registrar una mascota antes de agendar."
                        } else {
                            // MVP: Usamos la primera mascota encontrada
                            val mascota = mascotas.first()

                            // 3. Buscar Servicio
                            val servicios = servicioMedicoRepository.findByActivoTrue()
                            val servicio = servicios.find { it.nombre.equals("Consulta General", ignoreCase = true) }
                                ?: servicios.firstOrNull()

                            if (servicio == null) {
                                "Error: No hay servicios médicos activos disponibles para agendar."
                            } else {
                                // 4. Crear Reserva
                                val detalles =
                                    listOf(DetalleReservaRequest(servicioId = servicio.id!!, mascotaId = mascota.id))
                                val tutorPayload = JwtPayload(
                                    userId = userDb.id!!,
                                    email = userDb.email,
                                    role = userDb.role,
                                    expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
                                )

                                logger.info("[DIAGNOSTICO_TOOL] Iniciando creación de reserva para ${userDb.email} en $fechaHoraInstant")
                                val resultado = reservaService.crearReserva(
                                    detallesRequest = detalles,
                                    fechaHoraInicio = fechaHoraInstant,
                                    origen = OrigenCita.WHATSAPP,
                                    tutor = tutorPayload
                                )
                                logger.info("[DIAGNOSTICO_TOOL] Reserva creada EXITOSAMENTE. ID: ${resultado.cita.id}")
                                "Reserva creada exitosamente para ${mascota.nombre} el $fechaHoraStr. Por favor realiza el pago aquí para confirmar: ${resultado.paymentUrl}"
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("[CRITICAL_FAILURE] Error creando reserva desde IA", e)
                        "Ocurrió un error al intentar crear la reserva: ${e.message}"
                    }
                } else {
                    if (userDb == null) "Error: No se pudo identificar al usuario para agendar."
                    else "Error: Fecha y hora requerida."
                }
            }

            else -> "Error: Función desconocida o argumentos inválidos."
        }

        return Part.builder()
            .functionResponse(
                FunctionResponse.builder()
                    .name(funcName)
                    .response(mapOf("result" to resultText))
                    .build()
            )
            .build()
    }

    private fun crearHerramientas(): List<Tool> {
        // Schema types
        val propFecha = Schema.builder()
            .type(com.google.genai.types.Type.Known.STRING)
            .description("Fecha en formato YYYY-MM-DD")
            .build()

        val propFechaHora = Schema.builder()
            .type(com.google.genai.types.Type.Known.STRING)
            .description("Fecha y hora en formato ISO-8601 (ej: 2025-12-08T10:00)")
            .build()

        // Tool 1: consultar_disponibilidad
        val funcConsultar = FunctionDeclaration.builder()
            .name("consultar_disponibilidad")
            .description("Consulta disponibilidad de horas médicas en la clínica.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(mapOf("fecha" to propFecha))
                    .required(listOf("fecha"))
                    .build()
            )
            .build()

        // Tool 2: reservar_cita
        val funcReservar = FunctionDeclaration.builder()
            .name("reservar_cita")
            .description("Usa esta herramienta cuando el usuario confirme una hora específica para agendar.")
            .parameters(
                Schema.builder()
                    .type(com.google.genai.types.Type.Known.OBJECT)
                    .properties(mapOf("fechaHora" to propFechaHora))
                    .required(listOf("fechaHora"))
                    .build()
            )
            .build()

        return listOf(
            Tool.builder()
                .functionDeclarations(listOf(funcConsultar, funcReservar))
                .build()
        )
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
package cl.clinipets.core.ia

import cl.clinipets.agendamiento.application.DisponibilidadService
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import com.google.genai.Client
import com.google.genai.types.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections

@Service
class VeterinaryAgentService(
    private val userRepository: UserRepository,
    private val mascotaRepository: MascotaRepository,
    private val disponibilidadService: DisponibilidadService,
    private val clinicZoneId: ZoneId,
    private val client: GenAiClientWrapper,
    @Value("\${gemini.model:gemini-2.0-flash-lite}") private val modelName: String
) {
    private val logger = LoggerFactory.getLogger(VeterinaryAgentService::class.java)

    init {
        logger.info("[IA_INIT] Servicio Agente Veterinario listo. Modelo: $modelName")
    }

    /**
     * Lógica principal del Chatbot (WhatsApp)
     */
    fun procesarMensaje(telefono: String, mensajeUsuario: String): String {
        logger.info("[IA_AGENT] Procesando mensaje para teléfono: $telefono")

        // 1. Recuperar contexto del usuario
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
            "Cliente Nuevo."
        }

        // 2. System Prompt del Agente
        val systemPromptText = """
            Eres el asistente virtual de la veterinaria "Clinipets". 
            Tu tono es cercano, profesional, empático y adaptado a Chile.
            
            Información del cliente: $contextoCliente
            Fecha de hoy: ${LocalDate.now()}
            
            Objetivos:
            1. Responder dudas sobre servicios veterinarios.
            2. Guiar al usuario para que agende hora en: https://clinipets.cl/agendar?phone=$telefono
            3. Si preguntan por DISPONIBILIDAD u HORAS para una fecha específica, USA la herramienta 'consultar_disponibilidad'.
            
            Reglas:
            - Sé conciso (máximo 3-4 oraciones).
            - Si no puedes usar la herramienta, di que no tienes acceso a la agenda en este momento.
        """.trimIndent()

        // 3. Configuración
        val tools = crearHerramientas()

        val systemInstructionContent = Content.builder()
            .role("system")
            .parts(listOf(Part.builder().text(systemPromptText).build()))
            .build()

        val config = GenerateContentConfig.builder()
            .tools(tools)
            .systemInstruction(systemInstructionContent)
            .temperature(0.7f)
            .build()

        // 4. Mensaje del Usuario
        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(mensajeUsuario).build()))
            .build()

        return try {
            // --- PRIMERA LLAMADA (Turno 1) ---
            val response1 = client.generateContent(modelName, userContent, config)

            // Desempaquetado seguro de la respuesta
            val candidates = response1.candidates().orElse(Collections.emptyList())
            val candidate = candidates.firstOrNull()
            val modelContent = candidate?.content()?.orElse(null)
            val parts = modelContent?.parts()?.orElse(Collections.emptyList()) ?: emptyList()

            // Buscar si la IA quiere ejecutar una función
            val functionCallPart = parts.firstOrNull { it.functionCall().isPresent }
            val functionCall = functionCallPart?.functionCall()?.orElse(null)

            if (functionCall != null) {
                val funcName = functionCall.name().orElse("unknown")
                logger.info("[IA_AGENT] La IA solicita ejecutar herramienta: $funcName")

                // 5. Ejecutar lógica de negocio
                val functionResponsePart = ejecutarHerramienta(functionCall)

                // 6. Construir mensaje con el resultado de la función
                val toolContent = Content.builder()
                    .role("function")
                    .parts(listOf(functionResponsePart))
                    .build()

                // 7. Segunda llamada (Turno 2) con historial: [User, Model(Call), Tool(Result)]
                val history = listOf(userContent, modelContent!!, toolContent)

                // Configuración para la respuesta final (sin tools para evitar loops)
                val finalConfig = GenerateContentConfig.builder()
                    .systemInstruction(systemInstructionContent)
                    .build()

                val response2 = client.generateContent(modelName, history, finalConfig)
                response2.text() ?: "No pude generar una respuesta final tras consultar la agenda."

            } else {
                // Respuesta directa sin uso de herramientas
                response1.text() ?: "Lo siento, no pude entenderte."
            }
        } catch (e: Exception) {
            logger.error("[IA_AGENT] Error al procesar mensaje", e)
            "Disculpa, estoy teniendo un problema técnico momentáneo."
        }
    }

    private fun ejecutarHerramienta(functionCall: FunctionCall): Part {
        val argsMap = functionCall.args().orElse(Collections.emptyMap())
        val fechaStr = argsMap["fecha"] as? String
        val funcName = functionCall.name().orElse("")

        val resultText = if (funcName == "consultar_disponibilidad" && fechaStr != null) {
            try {
                val fecha = LocalDate.parse(fechaStr)
                val slots = disponibilidadService.obtenerSlots(fecha, 30)
                if (slots.isEmpty()) "No quedan horas disponibles para el $fechaStr."
                else {
                    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(clinicZoneId)
                    val horas = slots.joinToString(", ") { formatter.format(it) }
                    "Horas disponibles el $fechaStr: $horas"
                }
            } catch (e: Exception) {
                "Fecha inválida o error en agenda."
            }
        } else {
            "Error: Función desconocida o argumentos inválidos."
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
        // Usamos Type.Known.STRING para evitar errores con el SDK generado
        val propFecha = Schema.builder()
            .type(com.google.genai.types.Type.Known.STRING)
            .description("Fecha en formato YYYY-MM-DD")
            .build()

        val functionDecl = FunctionDeclaration.builder()
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

        // Usamos listOf() para envolver, ya que Tool.builder() espera vararg o lista según versión,
        // pero con esta estructura suele compilar bien en Kotlin.
        return listOf(
            Tool.builder()
                .functionDeclarations(functionDecl)
                .build()
        )
    }

    /**
     * Lógica de Moderación de Nombres
     */
    fun esNombreInapropiado(texto: String): Boolean {
        logger.info("[IA_MODERATOR] 🔍 Analizando texto: '{}'", texto)

        val prompt = """
            Tarea: Moderación de contenido.
            Analiza si el siguiente nombre de usuario es ofensivo, un insulto, sexual, grosero o inapropiado en Chile o Latinoamérica.
            Texto: "$texto"
            Responde SOLO con un objeto JSON: {"inapropiado": boolean, "razon": "string"}
        """.trimIndent()

        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(prompt).build()))
            .build()

        val config = GenerateContentConfig.builder()
            .temperature(0.0f) // Máximo determinismo
            .responseMimeType("application/json") // Forzamos JSON
            .build()

        return try {
            val response = client.generateContent(modelName, userContent, config)
            val rawText = response.text() ?: "{}"

            // Logueamos la respuesta cruda para auditoría
            logger.debug("[IA_MODERATOR] Respuesta Raw: {}", rawText)

            // Validación simple (sin parsear todo el JSON para ser más rápido/robusto ante errores de formato)
            val esInapropiado = rawText.contains("\"inapropiado\": true")

            if (esInapropiado) {
                logger.warn("[IA_MODERATOR] ⛔ BLOQUEADO. Texto: '{}'. Razón en logs debug.", texto)
            } else {
                logger.info("[IA_MODERATOR] ✅ APROBADO. Texto: '{}'", texto)
            }

            esInapropiado
        } catch (e: Exception) {
            // Fail-open: Si la IA falla, permitimos el nombre pero logueamos el error grave
            logger.error("[IA_MODERATOR] 💥 ERROR al consultar Gemini. Se permite por defecto. Excepción: {}", e.message)
            false
        }
    }
}
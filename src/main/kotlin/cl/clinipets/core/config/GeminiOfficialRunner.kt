package cl.clinipets.core.config

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.ListModelsConfig
import com.google.genai.types.Part
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class GeminiOfficialRunner(
    @Value("\${gemini.api-key}") private val apiKey: String,
    @Value("\${gemini.model}") private val modelName: String
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(GeminiOfficialRunner::class.java)

    override fun run(vararg args: String?) {
        logger.info("--- GEMINI OFFICIAL SDK DIAGNOSTIC ---")

        try {
            val client = Client.builder().apiKey(apiKey).build()

            // 1. Listar Modelos
            logger.info("Consultando modelos disponibles...")
            try {
                // Se requiere config para list() en versión 1.29.0+
                val listConfig = ListModelsConfig.builder().build()
                val models = client.models.list(listConfig)

                var flashFound = false
                models.forEach { model ->
                    // Simplificación: Convertir a String para evitar problemas de tipos con name()
                    val modelStr = model.toString()
                    if (modelStr.contains("flash", ignoreCase = true)) {
                        logger.info("MODELO DETECTADO (raw): $modelStr")
                        flashFound = true
                    }
                }
                if (!flashFound) logger.warn("No se encontraron modelos con 'flash' en el nombre.")
            } catch (e: Exception) {
                logger.warn("Error listando modelos (puede ser normal si la API Key tiene scope restringido): ${e.message}")
            }

            // 2. Prueba de Generación
            logger.info("Probando generación con modelo configurado: $modelName")

            val content = Content.builder()
                .parts(listOf(Part.builder().text("Responde solo la palabra: CONECTADO").build()))
                .build()

            val config = GenerateContentConfig.builder().build()
            val response = client.models.generateContent(modelName, content, config)

            logger.info("✅ GEMINI RESPONDIÓ: ${response.text()}")

        } catch (e: Exception) {
            logger.error("❌ ERROR CRÍTICO GEMINI SDK: ", e)
        }
    }
}

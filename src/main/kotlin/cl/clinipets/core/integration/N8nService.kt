package cl.clinipets.core.integration

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.http.MediaType

@Service
class N8nService(
    @Value("\${app.n8n.webhook-url}") private val webhookUrl: String,
    @Value("\${app.security.n8n-key}") private val n8nApiKey: String
) {
    private val logger = LoggerFactory.getLogger(N8nService::class.java)
    private val restClient = RestClient.create()

    private data class ValidationRequest(val tipo: String, val current: String, val new: String)
    private data class ValidationResponse(val aprobado: Boolean)

    fun validarNombre(nombreActual: String, nuevoNombre: String): Boolean {
        if (webhookUrl.isBlank() || n8nApiKey.isBlank()) {
            logger.warn("[N8N_VALIDATION] El servicio está deshabilitado (URL o API Key no configuradas). Aprobando por defecto.")
            return true
        }

        logger.info("[N8N_VALIDATION] Validando cambio de nombre: '{}' -> '{}'", nombreActual, nuevoNombre)

        return try {
            val response = restClient.post()
                .uri(webhookUrl)
                .header("X-API-KEY", n8nApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ValidationRequest("VALIDAR_NOMBRE", nombreActual, nuevoNombre))
                .retrieve()
                .body(ValidationResponse::class.java)

            val aprobado = response?.aprobado ?: true // Fail-safe: si la respuesta es nula, aprueba.
            logger.info("[N8N_VALIDATION] Resultado de la IA: {}", if (aprobado) "APROBADO" else "RECHAZADO")
            aprobado
        } catch (ex: Exception) {
            logger.error("[N8N_VALIDATION] Falló la comunicación con n8n. Aprobando por seguridad (fail-safe).", ex)
            true // Fail-safe
        }
    }
}


package cl.clinipets.core.integration.meta

import cl.clinipets.core.config.MetaProperties
import cl.clinipets.core.ia.VeterinaryAgentService
import cl.clinipets.core.integration.meta.dto.WebhookObject
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class MetaService(
    private val metaProperties: MetaProperties,
    private val veterinaryAgentService: VeterinaryAgentService,
    restClientBuilder: RestClient.Builder
) {
    private val logger = LoggerFactory.getLogger(MetaService::class.java)
    private val restClient = restClientBuilder.build()

    @Async
    fun processWebhook(payload: WebhookObject) {
        try {
            payload.entry.forEach { entry ->
                entry.changes.forEach { change ->
                    val value = change.value
                    if (value != null) {
                        // Procesar Mensajes Entrantes
                        value.messages?.forEach { message ->
                            // Validar timestamp para evitar procesar mensajes antiguos (opcional, pero buena práctica)
                            // Por ahora procesamos todo lo que llega

                            if (message.type == "text" && message.text != null) {
                                val userPhone = message.from
                                val userMessage = message.text.body
                                logger.info("[META_WEBHOOK] Mensaje de $userPhone: $userMessage")

                                // Llamar al Agente IA
                                val respuestaIa = veterinaryAgentService.procesarMensaje(userPhone, userMessage)

                                // Responder al usuario
                                enviarMensaje(userPhone, respuestaIa)
                            } else {
                                logger.debug("[META_WEBHOOK] Mensaje recibido tipo: ${message.type} (Ignorado)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[META_WEBHOOK] Error procesando payload", e)
        }
    }

    private fun enviarMensaje(destinatario: String, texto: String) {
        if (metaProperties.phoneNumberId.isBlank() || metaProperties.accessToken.isBlank()) {
            logger.warn("[META_SEND] Credenciales de Meta no configuradas. No se envió respuesta.")
            return
        }

        val url = "https://graph.facebook.com/v17.0/${metaProperties.phoneNumberId}/messages"

        val body = mapOf(
            "messaging_product" to "whatsapp",
            "recipient_type" to "individual",
            "to" to destinatario,
            "type" to "text",
            "text" to mapOf(
                "preview_url" to false,
                "body" to texto
            )
        )

        try {
            restClient.post()
                .uri(url)
                .header("Authorization", "Bearer ${metaProperties.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity()

            logger.info("[META_SEND] Respuesta enviada a $destinatario")
        } catch (e: Exception) {
            logger.error("[META_SEND] Error enviando mensaje a $destinatario", e)
        }
    }
}

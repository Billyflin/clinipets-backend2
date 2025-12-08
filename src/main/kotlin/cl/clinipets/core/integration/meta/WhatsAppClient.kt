package cl.clinipets.core.integration.meta

import cl.clinipets.core.config.MetaProperties
import cl.clinipets.core.integration.meta.dto.*
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class WhatsAppClient(
    private val metaProperties: MetaProperties,
    restClientBuilder: RestClient.Builder
) {
    private val logger = LoggerFactory.getLogger(WhatsAppClient::class.java)
    private val restClient = restClientBuilder.build()

    /**
     * Envia un mensaje de texto simple.
     */
    fun enviarMensaje(destinatario: String, texto: String) {
        val request = WhatsAppMessageReq(
            to = destinatario,
            type = "text",
            text = TextContent(body = texto)
        )
        enviarRequest(request)
    }

    /**
     * Envia mensaje con botones (máximo 3).
     */
    fun enviarBotones(destinatario: String, texto: String, opciones: Map<String, String>) {
        // Validar máximo 3 botones
        val botonesSafe = opciones.entries.take(3).map { (id, titulo) ->
            InteractiveButton(
                reply = InteractiveButtonReply(
                    id = id,
                    title = titulo.take(20) // Meta limita titulos de botones a 20 chars
                )
            )
        }

        val interactive = InteractiveContent(
            type = "button",
            body = InteractiveBody(text = texto),
            action = InteractiveAction(buttons = botonesSafe)
        )

        val request = WhatsAppMessageReq(
            to = destinatario,
            type = "interactive",
            interactive = interactive
        )
        enviarRequest(request)
    }

    /**
     * Envia mensaje tipo lista (máximo 10 opciones).
     */
    fun enviarLista(destinatario: String, texto: String, botonMenu: String, opciones: Map<String, String>) {
        // Validar máximo 10 opciones
        val filas = opciones.entries.take(10).map { (id, titulo) ->
            InteractiveRow(
                id = id,
                title = titulo.take(24) // Meta limita titulos de lista a 24 chars
            )
        }

        val seccion = InteractiveSection(
            title = "Opciones",
            rows = filas
        )

        val interactive = InteractiveContent(
            type = "list",
            body = InteractiveBody(text = texto),
            action = InteractiveAction(
                button = botonMenu.take(20),
                sections = listOf(seccion)
            )
        )

        val request = WhatsAppMessageReq(
            to = destinatario,
            type = "interactive",
            interactive = interactive
        )
        enviarRequest(request)
    }

    private fun enviarRequest(request: WhatsAppMessageReq) {
        if (metaProperties.phoneNumberId.isBlank() || metaProperties.accessToken.isBlank()) {
            logger.warn("[META_SEND] Credenciales de Meta no configuradas. No se envió mensaje a ${request.to}.")
            return
        }

        val url = "https://graph.facebook.com/v17.0/${metaProperties.phoneNumberId}/messages"

        try {
            logger.info("[META_SEND] Enviando request a Meta: {}", request)
            restClient.post()
                .uri(url)
                .header("Authorization", "Bearer ${metaProperties.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()

            logger.info("[META_SEND] Mensaje (${request.type}) enviado a ${request.to}")
        } catch (e: RestClientResponseException) {
            logger.error(
                "[META_SEND] Error API Meta enviando mensaje a ${request.to}. Status: ${e.statusCode}, Body: ${e.responseBodyAsString}",
                e
            )
        } catch (e: Exception) {
            logger.error("[META_SEND] Error inesperado enviando mensaje a ${request.to}", e)
        }
    }
}

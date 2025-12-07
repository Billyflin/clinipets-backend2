package cl.clinipets.core.integration.meta

import cl.clinipets.core.config.MetaProperties
import cl.clinipets.core.ia.VeterinaryAgentService
import cl.clinipets.core.integration.meta.dto.*
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
                            val userPhone = message.from
                            var userMessage: String? = null

                            // Caso 1: Texto simple
                            if (message.type == "text" && message.text != null) {
                                userMessage = message.text.body
                            }
                            // Caso 2: Respuesta Interactiva (Botón o Lista)
                            else if (message.type == "interactive" && message.interactive != null) {
                                val interactive = message.interactive
                                val replyId = interactive.listReply?.id ?: interactive.buttonReply?.id
                                val replyTitle = interactive.listReply?.title ?: interactive.buttonReply?.title

                                if (replyId != null && replyId.startsWith("RES_")) {
                                    // Simulamos que el usuario escribió su elección explícitamente para que la IA entienda
                                    userMessage = "El usuario seleccionó la hora ID: $replyId ($replyTitle)"
                                    logger.info("[META_WEBHOOK] Interacción recibida de $userPhone: $replyId")
                                }
                            }

                            if (userMessage != null) {
                                logger.info("[META_WEBHOOK] Procesando mensaje de $userPhone: $userMessage")
                                procesarYResponder(userPhone, userMessage)
                            } else {
                                logger.debug("[META_WEBHOOK] Mensaje recibido tipo: ${message.type} (Ignorado o sin contenido procesable)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[META_WEBHOOK] Error procesando payload", e)
        }
    }

    private fun procesarYResponder(userPhone: String, userMessage: String) {
        // Llamar al Agente IA
        val respuestaIa = veterinaryAgentService.procesarMensaje(userPhone, userMessage)

        // Responder al usuario según tipo
        when (respuestaIa) {
            is cl.clinipets.core.ia.AgentResponse.Text -> {
                enviarMensaje(userPhone, respuestaIa.content)
            }

            is cl.clinipets.core.ia.AgentResponse.ListOptions -> {
                enviarLista(
                    destinatario = userPhone,
                    texto = respuestaIa.text,
                    botonMenu = respuestaIa.buttonLabel,
                    opciones = respuestaIa.options
                )
            }
        }
    }

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
            restClient.post()
                .uri(url)
                .header("Authorization", "Bearer ${metaProperties.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()

            logger.info("[META_SEND] Mensaje (${request.type}) enviado a ${request.to}")
        } catch (e: Exception) {
            logger.error("[META_SEND] Error enviando mensaje a ${request.to}", e)
        }
    }
}

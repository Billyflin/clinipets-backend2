package cl.clinipets.core.integration.meta

import cl.clinipets.core.config.MetaProperties
import cl.clinipets.core.integration.meta.dto.WebhookObject
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/meta/webhook")
class MetaWebhookController(
    private val metaProperties: MetaProperties,
    private val metaService: MetaService
) {
    private val logger = LoggerFactory.getLogger(MetaWebhookController::class.java)

    /**
     * Verificación del Webhook (Handshake)
     */
    @GetMapping
    fun verifyWebhook(
        @RequestParam("hub.mode") mode: String?,
        @RequestParam("hub.verify_token") token: String?,
        @RequestParam("hub.challenge") challenge: String?
    ): ResponseEntity<String> {
        logger.info("[META_HANDSHAKE] Verificando webhook. Mode: $mode, Token: $token")

        if (mode == "subscribe" && token == metaProperties.verifyToken) {
            logger.info("[META_HANDSHAKE] Verificación exitosa.")
            return ResponseEntity.ok(challenge)
        } else {
            logger.warn("[META_HANDSHAKE] Fallo de verificación. Token esperado: ${metaProperties.verifyToken}, Recibido: $token")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    /**
     * Recepción de Eventos (Mensajes, Estados, etc)
     */
    @PostMapping
    fun receiveWebhook(@RequestBody payload: WebhookObject): ResponseEntity<Void> {
        // Procesamiento asíncrono para responder rápido a Meta
        metaService.processWebhook(payload)

        // Siempre retornar 200 OK para evitar reintentos de Meta
        return ResponseEntity.ok().build()
    }
}

package cl.clinipets.pagos.api

import cl.clinipets.pagos.application.PaymentWebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class NotificationData(val id: String?)
data class MercadoPagoNotification(
    val type: String?,
    val action: String?,
    val data: NotificationData?
)

@RestController
@RequestMapping("/api/v1/pagos/webhook")
class PaymentWebhookController(
    private val webhookService: PaymentWebhookService
) {
    private val logger = LoggerFactory.getLogger(PaymentWebhookController::class.java)

    @PostMapping
    fun handleMercadoPagoWebhook(@RequestBody notification: MercadoPagoNotification): ResponseEntity<Void> {
        logger.info("[MP_WEBHOOK] Notificación recibida: {}", notification)

        val data = notification.data
        if (notification.type == "payment" && data != null && !data.id.isNullOrBlank()) {
            val paymentId = data.id.toLongOrNull()
            if (paymentId != null) {
                logger.info("[MP_WEBHOOK] Procesando notificación para Payment ID: {}", paymentId)
                webhookService.processPaymentNotification(paymentId)
            } else {
                logger.warn("[MP_WEBHOOK] No se pudo parsear el ID del pago desde la notificación: {}", data.id)
            }
        } else {
            logger.debug("[MP_WEBHOOK] Ignorando notificación de tipo '{}'", notification.type)
        }

        // Respondemos inmediatamente con 200 OK para que MP no reintente.
        return ResponseEntity.ok().build()
    }
}

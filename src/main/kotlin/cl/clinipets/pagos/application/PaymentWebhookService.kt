package cl.clinipets.pagos.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PaymentWebhookService(
    private val mercadoPagoService: MercadoPagoService,
    private val citaRepository: CitaRepository
) {
    private val logger = LoggerFactory.getLogger(PaymentWebhookService::class.java)

    @Async
    @Transactional
    fun processPaymentNotification(paymentId: Long) {
        logger.info("[WEBHOOK_PROCESSOR] Obteniendo detalles para Payment ID: {}", paymentId)
        try {
            val paymentDetails = mercadoPagoService.getPaymentDetails(paymentId)

            if (paymentDetails == null) {
                logger.warn("[WEBHOOK_PROCESSOR] No se encontraron detalles para el pago {}", paymentId)
                return
            }

            if (paymentDetails.status == "approved") {
                val externalReference = paymentDetails.externalReference
                if (externalReference.isNullOrBlank()) {
                    logger.warn("[WEBHOOK_PROCESSOR] El pago {} fue aprobado pero no tiene external_reference.", paymentId)
                    return
                }

                // Ignoramos los pagos de saldo que no mapean a una cita directa
                if (externalReference.startsWith("SALDO-")) {
                    logger.info("[WEBHOOK_PROCESSOR] Ignorando notificación de pago de saldo para cita {}.", externalReference)
                    return
                }

                val citaId = try {
                    UUID.fromString(externalReference)
                } catch (e: IllegalArgumentException) {
                    logger.error("[WEBHOOK_PROCESSOR] La external_reference '{}' no es un UUID válido.", externalReference)
                    return
                }

                val citaOptional = citaRepository.findById(citaId)
                if (citaOptional.isPresent) {
                    val cita = citaOptional.get()
                    if (cita.estado == EstadoCita.PENDIENTE_PAGO) {
                        cita.estado = EstadoCita.CONFIRMADA
                        cita.mpPaymentId = paymentId
                        citaRepository.save(cita)
                        logger.info("[WEBHOOK_PROCESSOR] Cita {} actualizada a CONFIRMADA por webhook.", citaId)
                    } else {
                        logger.info("[WEBHOOK_PROCESSOR] La cita {} ya estaba en estado {}. No se actualiza.", citaId, cita.estado)
                    }
                } else {
                    logger.warn("[WEBHOOK_PROCESSOR] Se recibió notificación para una cita no encontrada: {}", citaId)
                }
            } else {
                logger.info("[WEBHOOK_PROCESSOR] El estado del pago {} es '{}'. No se procesa.", paymentId, paymentDetails.status)
            }
        } catch (e: Exception) {
            logger.error("[WEBHOOK_PROCESSOR] Error procesando notificación para Payment ID: {}", paymentId, e)
        }
    }
}


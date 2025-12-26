package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.events.ReservaCanceladaEvent
import cl.clinipets.agendamiento.domain.events.ReservaConfirmadaEvent
import cl.clinipets.core.notifications.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ReservaEventListener(
    private val notificationService: NotificationService,
    private val citaRepository: CitaRepository
) {
    private val logger = LoggerFactory.getLogger(ReservaEventListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservaConfirmada(event: ReservaConfirmadaEvent) {
        logger.info("[EVENT] Reserva confirmada: ${event.citaId}")
        val cita = citaRepository.findById(event.citaId).orElse(null) ?: return
        
        notificationService.enviarNotificacion(
            userId = cita.tutor.id!!,
            titulo = "✅ Reserva Confirmada",
            cuerpo = "Tu cita ha sido confirmada exitosamente. Te esperamos.",
            data = mapOf("citaId" to cita.id.toString(), "estado" to "CONFIRMADA")
        )
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservaCancelada(event: ReservaCanceladaEvent) {
        logger.info("[EVENT] Reserva cancelada: ${event.citaId}")
        val cita = citaRepository.findById(event.citaId).orElse(null) ?: return

        notificationService.enviarNotificacion(
            userId = cita.tutor.id!!,
            titulo = "❌ Reserva Cancelada",
            cuerpo = "Tu cita ha sido cancelada. Motivo: ${event.motivo}",
            data = mapOf("citaId" to cita.id.toString(), "estado" to "CANCELADA")
        )
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onConsultaFinalizada(event: cl.clinipets.agendamiento.domain.events.ConsultaFinalizadaEvent) {
        logger.info("[EVENT] Consulta Finalizada: ${event.citaId}")
        val cita = citaRepository.findById(event.citaId).orElse(null) ?: return

        notificationService.enviarNotificacion(
            userId = cita.tutor.id!!,
            titulo = "🩺 Atención Finalizada",
            cuerpo = "Tu atención ha finalizado. Gracias por confiar en nosotros.",
            data = mapOf("citaId" to cita.id.toString(), "estado" to "FINALIZADA")
        )
    }
}
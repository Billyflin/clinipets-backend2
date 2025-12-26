package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.events.ReservaCanceladaEvent
import cl.clinipets.agendamiento.domain.events.ReservaConfirmadaEvent
import cl.clinipets.agendamiento.domain.events.ReservaCreadaEvent
import cl.clinipets.core.notifications.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class ReservaEventListener(
    private val citaRepository: CitaRepository,
    private val notificationService: NotificationService,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(ReservaEventListener::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    @Async
    @EventListener
    fun onReservaCreada(event: ReservaCreadaEvent) {
        logger.info("[EVENT] ReservaCreada: ${event.citaId}")

        val cita = citaRepository.findById(event.citaId).orElse(null) ?: return
        val fechaFormateada = cita.fechaHoraInicio
            .atZone(clinicZoneId)
            .format(dateFormatter)

        notificationService.enviarNotificacion(
            userId = cita.tutorId,
            titulo = "✅ Reserva confirmada",
            cuerpo = "Tu cita está agendada para el $fechaFormateada",
            data = mapOf(
                "type" to "reserva_creada",
                "citaId" to cita.id.toString()
            )
        )
    }

    @Async
    @EventListener
    fun onReservaCancelada(event: ReservaCanceladaEvent) {
        logger.info("[EVENT] ReservaCancelada: ${event.citaId}")

        val cita = citaRepository.findById(event.citaId).orElse(null) ?: return

        notificationService.enviarNotificacion(
            userId = cita.tutorId,
            titulo = "❌ Cita cancelada",
            cuerpo = "Tu cita ha sido cancelada. ${event.motivo}",
            data = mapOf(
                "type" to "reserva_cancelada",
                "citaId" to cita.id.toString()
            )
        )
    }
}

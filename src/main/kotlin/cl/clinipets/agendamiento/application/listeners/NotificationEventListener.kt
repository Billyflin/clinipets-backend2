package cl.clinipets.agendamiento.application.listeners

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.events.ReservaCanceladaEvent
import cl.clinipets.agendamiento.domain.events.ReservaConfirmadaEvent
import cl.clinipets.agendamiento.domain.events.ReservaCreadaEvent
import cl.clinipets.core.config.ClinicProperties
import cl.clinipets.core.integration.NotificationService
import cl.clinipets.core.integration.meta.WhatsAppClient
import cl.clinipets.identity.domain.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Component
class NotificationEventListener(
    private val notificationService: NotificationService,
    private val citaRepository: CitaRepository,
    private val userRepository: UserRepository,
    private val clinicProperties: ClinicProperties,
    private val whatsAppClient: WhatsAppClient,
    private val clinicZoneId: ZoneId
) {

    private val logger = LoggerFactory.getLogger(NotificationEventListener::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener
    fun onReservaCreada(event: ReservaCreadaEvent) {
        val cita = citaRepository.findById(event.citaId).orElse(null)
        if (cita == null) {
            logger.warn("[EVENT] ReservaCreadaEvent recibido pero no se encontró cita {}", event.citaId)
            return
        }
        val tutorNombre = userRepository.findById(cita.tutorId)
            .map { it.name }
            .orElse(null)
            ?.takeIf { it.isNotBlank() }
            ?: "Cliente"

        val hora = cita.fechaHoraInicio.atZone(clinicZoneId)
            .toLocalTime()
            .truncatedTo(ChronoUnit.MINUTES)

        notificationService.enviarNotificacionAStaff(
            "Nueva Reserva",
            "Cliente $tutorNombre agendó para $hora",
            mapOf(
                "type" to "STAFF_CITA_DETAIL",
                "citaId" to event.citaId.toString()
            )
        )

        // Ejemplo simple de WhatsApp informativo a staff (opcional)
        runCatching {
            val mensaje =
                "Nueva reserva en ${clinicProperties.name}: $tutorNombre reservó para las $hora. ID ${event.citaId}"
            whatsAppClient.enviarMensaje(
                clinicProperties.phone.takeIf { it.isNotBlank() } ?: return@runCatching,
                mensaje
            )
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener
    fun onReservaConfirmada(event: ReservaConfirmadaEvent) {
        val cita = citaRepository.findById(event.citaId).orElse(null)
        if (cita == null) {
            logger.warn("[EVENT] ReservaConfirmadaEvent sin cita {}", event.citaId)
            return
        }
        val servicioNombre = cita.detalles.firstOrNull()?.servicio?.nombre ?: "tu reserva"
        notificationService.enviarNotificacion(
            cita.tutorId,
            "¡Reserva Confirmada!",
            "Tu cita para $servicioNombre está lista",
            data = mapOf("type" to "CLIENT_RESERVATIONS")
        )
        notificarStaffResumen(cita, "Pago confirmado", "La reserva quedó en estado ${cita.estado}")
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener
    fun onReservaCancelada(event: ReservaCanceladaEvent) {
        val cita = citaRepository.findById(event.citaId).orElse(null)
        if (cita == null) {
            logger.warn("[EVENT] ReservaCanceladaEvent sin cita {}", event.citaId)
            return
        }
        val detalle = if (event.motivo.isNotBlank()) event.motivo else "Reserva cancelada"
        notificarStaffResumen(cita, "Reserva cancelada", detalle)
    }

    private fun notificarStaffResumen(cita: Cita, titulo: String, detalle: String) {
        val resumen = resumenCita(cita)
        notificationService.enviarNotificacionAStaff(
            titulo,
            "Cita ${cita.id}: $detalle ($resumen)."
        )
    }

    private fun resumenCita(cita: Cita): String {
        val fechaLocal = cita.fechaHoraInicio.atZone(clinicZoneId)
        val hora = fechaLocal.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        val servicioNombre = cita.detalles.firstOrNull()?.servicio?.nombre ?: "cita"
        return "$servicioNombre el ${fechaLocal.toLocalDate()} a las $hora"
    }
}

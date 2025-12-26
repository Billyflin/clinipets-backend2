package cl.clinipets.agendamiento.scheduler

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.core.notifications.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class RecordatorioScheduler(
    private val citaRepository: CitaRepository,
    private val notificationService: NotificationService,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(RecordatorioScheduler::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /**
     * Envía recordatorio 24 horas antes de cada cita
     * Cron: Ejecuta todos los días a las 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    fun enviarRecordatoriosDiarios() {
        logger.info("[SCHEDULER] Iniciando envío de recordatorios diarios")

        val ahora = Instant.now()
        val en24Horas = ahora.plus(24, ChronoUnit.HOURS)
        val en25Horas = en24Horas.plus(1, ChronoUnit.HOURS)

        // Buscar citas entre 24-25 horas desde ahora
        val citas = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(
            en24Horas,
            en25Horas
        ).filter { it.estado == EstadoCita.CONFIRMADA }

        logger.info("[SCHEDULER] Encontradas ${citas.size} citas para recordatorio")

        citas.forEach { cita ->
            val fechaFormateada = cita.fechaHoraInicio
                .atZone(clinicZoneId)
                .format(dateFormatter)

            notificationService.enviarNotificacion(
                userId = cita.tutorId,
                titulo = "⏰ Recordatorio de cita",
                cuerpo = "Tu cita es mañana a las $fechaFormateada. ¡No faltes!",
                data = mapOf(
                    "type" to "recordatorio_24h",
                    "citaId" to cita.id.toString()
                )
            )
        }
    }

    /**
     * Envía recordatorio 1 hora antes de cada cita
     * Cron: Ejecuta cada hora
     */
    @Scheduled(cron = "0 0 * * * *")
    fun enviarRecordatoriosUrgentes() {
        logger.info("[SCHEDULER] Iniciando recordatorios de 1 hora")

        val ahora = Instant.now()
        val en1Hora = ahora.plus(1, ChronoUnit.HOURS)
        val en90Minutos = en1Hora.plus(30, ChronoUnit.MINUTES)

        val citas = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(
            en1Hora,
            en90Minutos
        ).filter { it.estado == EstadoCita.CONFIRMADA }

        logger.info("[SCHEDULER] Encontradas ${citas.size} citas próximas")

        citas.forEach { cita ->
            val fechaFormateada = cita.fechaHoraInicio
                .atZone(clinicZoneId)
                .format(dateFormatter)

            notificationService.enviarNotificacion(
                userId = cita.tutorId,
                titulo = "🔔 Tu cita es pronto",
                cuerpo = "Tu cita es en 1 hora ($fechaFormateada). Te esperamos!",
                data = mapOf(
                    "type" to "recordatorio_1h",
                    "citaId" to cita.id.toString()
                )
            )
        }
    }
}

package cl.clinipets.core.scheduler

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class DataCleanupScheduler(
    private val citaRepository: CitaRepository
) {
    private val logger = LoggerFactory.getLogger(DataCleanupScheduler::class.java)

    /**
     * Cancela automáticamente citas CONFIRMADAS que pasaron hace más de 2 horas
     * Cron: Ejecuta cada 30 minutos
     */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    fun cancelarCitasExpiradas() {
        logger.info("[CLEANUP] Buscando citas expiradas...")

        val hace2Horas = Instant.now().minus(2, ChronoUnit.HOURS)

        val citasExpiradas = citaRepository.findByEstadoAndCreatedAtBefore(
            EstadoCita.CONFIRMADA,
            hace2Horas
        ).filter { 
            it.fechaHoraFin < Instant.now().minus(2, ChronoUnit.HOURS)
        }

        if (citasExpiradas.isEmpty()) {
            logger.debug("[CLEANUP] No hay citas expiradas")
            return
        }

        logger.warn("[CLEANUP] Marcando ${citasExpiradas.size} citas expiradas como NO_ASISTIO")

        citasExpiradas.forEach { cita ->
            cita.estado = EstadoCita.NO_ASISTIO // ← Cambiar de CANCELADA a NO_ASISTIO
            citaRepository.save(cita)
            logger.info("[CLEANUP] Cita ${cita.id} marcada como NO_ASISTIO automáticamente")
        }
    }

    /**
     * Elimina tokens FCM inválidos cada semana
     * Cron: Domingos a las 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    fun limpiarTokensInvalidos() {
        logger.info("[CLEANUP] Limpieza de tokens FCM (placeholder)")
        // TODO: Implementar lógica para detectar tokens inválidos
        // Requiere tracking de errores de Firebase
    }
}

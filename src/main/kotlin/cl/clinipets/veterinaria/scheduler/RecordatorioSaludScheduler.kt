package cl.clinipets.veterinaria.scheduler

import cl.clinipets.core.notifications.NotificationService
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class RecordatorioSaludScheduler(
    private val fichaRepository: FichaClinicaRepository,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(RecordatorioSaludScheduler::class.java)

    /**
     * Revisa vacunas y controles próximos (mañana y en 7 días)
     * Cron: Todos los días a las 10:00 AM
     */
    @Scheduled(cron = "0 0 10 * * *")
    fun enviarRecordatoriosSalud() {
        logger.info("[SALUD_SCHEDULER] Iniciando revisión de recordatorios sanitarios")
        val hoy = LocalDate.now()
        val manana = hoy.plusDays(1)
        val enUnaSemana = hoy.plusDays(7)

        val fichas = fichaRepository.findAll() // En producción optimizar con Query específica

        // 1. Vacunas
        fichas.filter { it.fechaProximaVacuna != null }.forEach { ficha ->
            val fecha = ficha.fechaProximaVacuna!!
            val mascota = ficha.mascota
            
            if (fecha == manana) {
                enviarPush(mascota.tutor.id!!, "💉 Vacuna mañana", "Recuerda que a ${mascota.nombre} le toca vacuna mañana (${fmt(fecha)})")
            } else if (fecha == enUnaSemana) {
                enviarPush(mascota.tutor.id!!, "📅 Vacuna próxima", "En 7 días le toca vacuna a ${mascota.nombre}. ¡Agenda tu hora!")
            }
        }

        // 2. Controles
        fichas.filter { it.fechaProximoControl != null }.forEach { ficha ->
            val fecha = ficha.fechaProximoControl!!
            val mascota = ficha.mascota

            if (fecha == manana) {
                enviarPush(mascota.tutor.id!!, "🩺 Control mañana", "Mañana toca control veterinario para ${mascota.nombre}")
            }
        }
    }

    private fun enviarPush(userId: java.util.UUID, titulo: String, body: String) {
        try {
            notificationService.enviarNotificacion(userId, titulo, body, mapOf("type" to "salud"))
        } catch (e: Exception) {
            logger.warn("Error enviando push salud a user $userId: ${e.message}")
        }
    }

    private fun fmt(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("dd/MM"))
}

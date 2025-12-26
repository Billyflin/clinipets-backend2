package cl.clinipets.veterinaria.scheduler

import cl.clinipets.core.notifications.NotificationService
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class RecordatorioSaludScheduler(
    private val fichaClinicaRepository: FichaClinicaRepository,
    private val notificationService: NotificationService,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(RecordatorioSaludScheduler::class.java)

    /**
     * Revisa planes sanitarios y envía recordatorios 7 días antes
     * Ejecuta cada día a las 10:00 AM
     */
    @Scheduled(cron = "0 0 10 * * *")
    fun procesarRecordatoriosSalud() {
        val hoy = LocalDate.now(clinicZoneId)
        val recordatorioEn = hoy.plusDays(7)
        
        logger.info("[SALUD-SCHEDULER] Buscando hitos sanitarios para el día: $recordatorioEn")

        val todasFichas = fichaClinicaRepository.findAll() // Opcional: Optimizar con query específica
        
        todasFichas.forEach { ficha ->
            val plan = ficha.planSanitario
            val mascota = ficha.mascota
            
            // 1. Recordatorio Vacuna
            if (plan.esVacuna && plan.fechaProximaVacuna == recordatorioEn) {
                notificationService.enviarNotificacion(
                    userId = mascota.tutor.id!!,
                    titulo = "💉 Vacuna próxima para ${mascota.nombre}",
                    cuerpo = "Recuerda que en 7 días corresponde el refuerzo de: ${plan.nombreVacuna}",
                    data = mapOf("mascotaId" to mascota.id.toString(), "type" to "salud_vacuna")
                )
            }

            // 2. Recordatorio Control
            if (plan.fechaProximoControl == recordatorioEn) {
                notificationService.enviarNotificacion(
                    userId = mascota.tutor.id!!,
                    titulo = "🩺 Control médico para ${mascota.nombre}",
                    cuerpo = "Su próximo control preventivo está programado para el día $recordatorioEn",
                    data = mapOf("mascotaId" to mascota.id.toString(), "type" to "salud_control")
                )
            }
        }
    }
}
package cl.clinipets.agendamiento.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReservaCleanupScheduler(
    private val reservaService: ReservaService
) {

    @Scheduled(fixedRate = 900000) // 15 minutos = 15 * 60 * 1000 = 900000 ms
    fun cleanup() {
        reservaService.cancelarReservasExpiradas()
    }
}

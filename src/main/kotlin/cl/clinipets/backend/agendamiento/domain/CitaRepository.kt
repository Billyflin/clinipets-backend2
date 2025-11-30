package cl.clinipets.backend.agendamiento.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface CitaRepository : JpaRepository<Cita, UUID> {
    fun findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<Cita>
}

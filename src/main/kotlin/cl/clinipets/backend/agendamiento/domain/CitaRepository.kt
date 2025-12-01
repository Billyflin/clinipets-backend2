package cl.clinipets.backend.agendamiento.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface CitaRepository : JpaRepository<Cita, UUID> {
    fun findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(
        start: Instant,
        end: Instant
    ): List<Cita>

    fun findAllByTutorIdOrderByFechaHoraInicioDesc(tutorId: UUID): List<Cita>
}

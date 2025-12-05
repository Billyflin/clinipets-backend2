package cl.clinipets.agendamiento.domain

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

    fun findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(start: Instant, end: Instant): List<Cita>

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Cita c JOIN c.detalles d WHERE d.mascota.id = :mascotaId ORDER BY c.fechaHoraInicio DESC")
    fun findAllByMascotaId(mascotaId: UUID): List<Cita>

    fun findByEstadoAndCreatedAtBefore(estado: EstadoCita, date: Instant): List<Cita>
}

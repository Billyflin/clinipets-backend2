package cl.clinipets.agendamiento.domain

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface CitaRepository : JpaRepository<Cita, UUID> {
    fun findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(
        start: Instant,
        end: Instant
    ): List<Cita>

    @EntityGraph(attributePaths = ["detalles", "detalles.servicio", "detalles.mascota"])
    fun findAllByTutorIdOrderByFechaHoraInicioDesc(tutorId: UUID): List<Cita>

    fun findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(start: Instant, end: Instant): List<Cita>

    @Query(
        """
        SELECT DISTINCT c FROM Cita c
        JOIN c.detalles dFilter
        LEFT JOIN FETCH c.detalles d
        LEFT JOIN FETCH d.servicio
        LEFT JOIN FETCH d.mascota
        WHERE dFilter.mascota.id = :mascotaId
        ORDER BY c.fechaHoraInicio DESC
        """
    )
    fun findAllByMascotaId(mascotaId: UUID): List<Cita>

    fun findByEstadoAndCreatedAtBefore(estado: EstadoCita, date: Instant): List<Cita>
}

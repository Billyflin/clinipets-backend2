package cl.clinipets.agendamiento.domain

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface CitaRepository : JpaRepository<Cita, UUID> {
    @Query("SELECT c FROM Cita c WHERE c.fechaHoraFin > :start AND c.fechaHoraInicio < :end")
    fun findOverlappingCitas(
        start: Instant,
        end: Instant
    ): List<Cita>

    @EntityGraph(attributePaths = ["detalles", "detalles.servicio", "detalles.mascota"])
    fun findAllByTutorIdOrderByFechaHoraInicioDesc(tutorId: UUID): List<Cita>

    fun findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(start: Instant, end: Instant): List<Cita>

    @Query("SELECT DISTINCT c FROM Cita c JOIN c.detalles d WHERE d.mascota.id = :mascotaId")
    @EntityGraph(attributePaths = ["detalles", "detalles.servicio", "detalles.mascota"])
    fun findAllByMascotaId(@Param("mascotaId") mascotaId: UUID): List<Cita>

    fun findByEstadoAndCreatedAtBefore(estado: EstadoCita, date: Instant): List<Cita>
}

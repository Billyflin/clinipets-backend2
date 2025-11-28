package cl.clinipets.backend.agendamiento.infraestructura

import cl.clinipets.backend.agendamiento.dominio.Cita
import cl.clinipets.backend.agendamiento.dominio.EstadoCita
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CitaRepository : JpaRepository<Cita, Long> {

    @Query("SELECT c FROM Cita c WHERE c.fechaHora >= :inicio AND c.fechaHora < :fin AND c.estado <> 'CANCELADA'")
    fun findCitasActivasEntre(inicio: LocalDateTime, fin: LocalDateTime): List<Cita>

    fun existsByFechaHoraAndEstadoNot(fechaHora: LocalDateTime, estado: EstadoCita): Boolean
}

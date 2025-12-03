package cl.clinipets.agendamiento.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface BloqueoAgendaRepository : JpaRepository<BloqueoAgenda, UUID> {
    fun findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(start: Instant, end: Instant): List<BloqueoAgenda>
}


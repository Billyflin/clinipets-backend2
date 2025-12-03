package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.BloqueoAgenda
import cl.clinipets.agendamiento.domain.BloqueoAgendaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class BloqueoService(
    private val bloqueoAgendaRepository: BloqueoAgendaRepository,
    private val clinicZoneId: ZoneId
) {

    @Transactional
    fun crearBloqueo(vetId: UUID, inicio: Instant, fin: Instant, motivo: String?): BloqueoAgenda {
        val bloqueo = BloqueoAgenda(
            veterinarioId = vetId,
            fechaHoraInicio = inicio,
            fechaHoraFin = fin,
            motivo = motivo
        )
        return bloqueoAgendaRepository.save(bloqueo)
    }

    @Transactional
    fun eliminarBloqueo(id: UUID) {
        bloqueoAgendaRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun obtenerBloqueos(fecha: LocalDate): List<BloqueoAgenda> {
        val inicioDia = fecha.atStartOfDay(clinicZoneId).toInstant()
        val finDia = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()
        return bloqueoAgendaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(inicioDia, finDia)
    }
}


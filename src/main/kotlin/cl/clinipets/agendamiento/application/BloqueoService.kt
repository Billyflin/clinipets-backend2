package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.BloqueoAgenda
import cl.clinipets.agendamiento.domain.BloqueoAgendaRepository
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(BloqueoService::class.java)

    @Transactional
    fun crearBloqueo(vetId: UUID, inicio: Instant, fin: Instant, motivo: String?): BloqueoAgenda {
        logger.debug("[BLOQUEO_SERVICE] Creando bloqueo. VetID: {}, Inicio: {}, Fin: {}", vetId, inicio, fin)
        val bloqueo = BloqueoAgenda(
            veterinarioId = vetId,
            fechaHoraInicio = inicio,
            fechaHoraFin = fin,
            motivo = motivo
        )
        val saved = bloqueoAgendaRepository.save(bloqueo)
        logger.info("[BLOQUEO_SERVICE] Bloqueo creado con ID: {}", saved.id)
        return saved
    }

    @Transactional
    fun eliminarBloqueo(id: UUID) {
        logger.debug("[BLOQUEO_SERVICE] Eliminando bloqueo ID: {}", id)
        bloqueoAgendaRepository.deleteById(id)
        logger.info("[BLOQUEO_SERVICE] Bloqueo eliminado")
    }

    @Transactional(readOnly = true)
    fun obtenerBloqueos(fecha: LocalDate): List<BloqueoAgenda> {
        val inicioDia = fecha.atStartOfDay(clinicZoneId).toInstant()
        val finDia = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()
        logger.debug("[BLOQUEO_SERVICE] Buscando bloqueos para: {}", fecha)
        return bloqueoAgendaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(inicioDia, finDia)
    }
}


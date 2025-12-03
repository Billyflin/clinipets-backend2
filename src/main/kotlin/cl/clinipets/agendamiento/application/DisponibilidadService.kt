package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.HorarioClinica
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class DisponibilidadService(
    private val citaRepository: CitaRepository
) {
    private val intervaloMinutos = 15L
    private val zoneId = ZoneId.systemDefault()

    @Transactional(readOnly = true)
    fun obtenerSlots(fecha: Instant, duracionMinutos: Int): List<Instant> {
        val fechaLocal = fecha.atZone(zoneId).toLocalDate()
        val ventana = HorarioClinica.ventanaPara(fechaLocal.dayOfWeek) ?: return emptyList()
        val (abre, cierra) = ventana

        val startOfDay = fechaLocal.atStartOfDay(zoneId).toInstant()
        val endOfDay = fechaLocal.plusDays(1).atStartOfDay(zoneId).toInstant()
        
        val citasDia = citaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(startOfDay, endOfDay)

        val slots = mutableListOf<Instant>()
        
        // Construir el cursor inicial: fecha + hora de apertura
        var cursor = fechaLocal.atTime(abre).atZone(zoneId).toInstant()
        val limiteCierre = fechaLocal.atTime(cierra).atZone(zoneId).toInstant()

        while (cursor.plus(duracionMinutos.toLong(), ChronoUnit.MINUTES) <= limiteCierre) {
            val inicio = cursor
            val fin = inicio.plus(duracionMinutos.toLong(), ChronoUnit.MINUTES)
            if (estaLibre(inicio, fin, citasDia)) {
                slots.add(cursor)
            }
            cursor = cursor.plus(intervaloMinutos, ChronoUnit.MINUTES)
        }
        return slots
    }

    private fun estaLibre(inicio: Instant, fin: Instant, citas: List<Cita>): Boolean =
        citas.none { cita ->
            inicio < cita.fechaHoraFin && fin > cita.fechaHoraInicio
        }
}

package cl.clinipets.backend.agendamiento.application

import cl.clinipets.backend.agendamiento.domain.Cita
import cl.clinipets.backend.agendamiento.domain.CitaRepository
import cl.clinipets.backend.agendamiento.domain.HorarioClinica
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class DisponibilidadService(
    private val citaRepository: CitaRepository
) {
    private val intervaloMinutos = 15L

    @Transactional(readOnly = true)
    fun obtenerSlots(fecha: LocalDate, duracionMinutos: Int): List<LocalTime> {
        val ventana = HorarioClinica.ventanaPara(fecha.dayOfWeek) ?: return emptyList()
        val (abre, cierra) = ventana

        val startOfDay = fecha.atTime(LocalTime.MIN)
        val endOfDay = fecha.atTime(LocalTime.MAX)
        val citasDia = citaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(startOfDay, endOfDay)

        val slots = mutableListOf<LocalTime>()
        var cursor = abre
        while (cursor.plusMinutes(duracionMinutos.toLong()) <= cierra) {
            val inicio = fecha.atTime(cursor)
            val fin = inicio.plusMinutes(duracionMinutos.toLong())
            if (estaLibre(inicio, fin, citasDia)) {
                slots.add(cursor)
            }
            cursor = cursor.plusMinutes(intervaloMinutos)
        }
        return slots
    }

    private fun estaLibre(inicio: LocalDateTime, fin: LocalDateTime, citas: List<Cita>): Boolean =
        citas.none { cita ->
            inicio < cita.fechaHoraFin && fin > cita.fechaHoraInicio
        }
}

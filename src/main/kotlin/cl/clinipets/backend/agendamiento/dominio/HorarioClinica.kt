package cl.clinipets.backend.agendamiento.dominio

import java.time.DayOfWeek
import java.time.LocalTime

object HorarioClinica {

    fun obtenerHorario(dia: DayOfWeek): RangoHorario? {
        return when (dia) {
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY -> RangoHorario(LocalTime.of(11, 0), LocalTime.of(13, 0))

            DayOfWeek.SATURDAY -> RangoHorario(LocalTime.of(10, 0), LocalTime.of(19, 0))
            DayOfWeek.SUNDAY -> null
        }
    }

    fun estaDentroDeHorario(fechaHora: java.time.LocalDateTime): Boolean {
        val horario = obtenerHorario(fechaHora.dayOfWeek) ?: return false
        val hora = fechaHora.toLocalTime()
        // Inicio inclusivo, Fin exclusivo (o inclusivo según regla de negocio, asumiremos que la cita debe terminar antes o en el cierre?)
        // Usualmente para slots, si cierra a las 13:00, la ultima cita de 30 min es a las 12:30.
        // Aquí validamos que el inicio de la cita esté dentro del rango permitido para INICIAR atención.
        // Si el rango es 11 a 13, asumimos que se atiende DE 11:00 A 13:00.
        // Una cita a las 13:00 estaría fuera si 13:00 es la hora de cierre.
        // Vamos a asumir que cierre es el límite superior.
        return !hora.isBefore(horario.inicio) && hora.isBefore(horario.fin)
    }

    data class RangoHorario(val inicio: LocalTime, val fin: LocalTime)
}

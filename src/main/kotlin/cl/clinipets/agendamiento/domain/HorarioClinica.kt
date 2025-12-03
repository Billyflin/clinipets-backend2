package cl.clinipets.agendamiento.domain

import java.time.DayOfWeek
import java.time.LocalTime

object HorarioClinica {
    private val horario: Map<DayOfWeek, Pair<LocalTime, LocalTime>> = mapOf(
        DayOfWeek.MONDAY to (LocalTime.of(11, 0) to LocalTime.of(13, 0)),
        DayOfWeek.TUESDAY to (LocalTime.of(11, 0) to LocalTime.of(13, 0)),
        DayOfWeek.WEDNESDAY to (LocalTime.of(11, 0) to LocalTime.of(13, 0)),
        DayOfWeek.THURSDAY to (LocalTime.of(11, 0) to LocalTime.of(13, 0)),
        DayOfWeek.FRIDAY to (LocalTime.of(11, 0) to LocalTime.of(13, 0)),
        DayOfWeek.SATURDAY to (LocalTime.of(10, 0) to LocalTime.of(19, 0))
    )

    fun ventanaPara(dia: DayOfWeek): Pair<LocalTime, LocalTime>? = horario[dia]
}

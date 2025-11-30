package cl.clinipets.backend.agendamiento.api

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class DisponibilidadResponse(
    val fecha: LocalDate,
    val servicioId: UUID,
    val slots: List<LocalTime>
)

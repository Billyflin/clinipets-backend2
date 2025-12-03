package cl.clinipets.agendamiento.api

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DisponibilidadResponse(
    val fecha: LocalDate,
    val servicioId: UUID?,
    val slots: List<Instant>
)

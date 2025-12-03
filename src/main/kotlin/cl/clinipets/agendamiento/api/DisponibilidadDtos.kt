package cl.clinipets.agendamiento.api

import java.time.Instant
import java.util.UUID

data class DisponibilidadResponse(
    val fecha: Instant,
    val servicioId: UUID?,
    val slots: List<Instant>
)

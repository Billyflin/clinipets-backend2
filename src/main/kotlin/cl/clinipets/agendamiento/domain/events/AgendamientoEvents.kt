package cl.clinipets.agendamiento.domain.events

import java.util.UUID

data class ReservaCreadaEvent(val citaId: UUID)

data class ReservaConfirmadaEvent(val citaId: UUID)

data class ReservaCanceladaEvent(val citaId: UUID, val motivo: String = "")

data class ConsultaFinalizadaEvent(
    val citaId: UUID,
    val items: List<CitaItem>
)

data class CitaItem(
    val servicioId: UUID,
    val cantidad: Int
)

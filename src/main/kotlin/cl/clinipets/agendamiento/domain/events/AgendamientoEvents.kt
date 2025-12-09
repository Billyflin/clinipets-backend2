package cl.clinipets.agendamiento.domain.events

import java.util.UUID

data class ReservaCreadaEvent(val citaId: UUID)

data class ReservaConfirmadaEvent(val citaId: UUID)

data class ReservaCanceladaEvent(val citaId: UUID, val motivo: String = "")

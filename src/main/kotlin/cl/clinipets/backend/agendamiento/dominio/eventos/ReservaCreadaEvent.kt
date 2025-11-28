package cl.clinipets.backend.agendamiento.dominio.eventos

import cl.clinipets.backend.agendamiento.dominio.Cita

data class ReservaCreadaEvent(val cita: Cita)

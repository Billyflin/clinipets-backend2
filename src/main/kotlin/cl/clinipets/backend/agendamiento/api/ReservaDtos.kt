package cl.clinipets.backend.agendamiento.api

import cl.clinipets.backend.agendamiento.domain.EstadoCita
import cl.clinipets.backend.agendamiento.domain.OrigenCita
import cl.clinipets.backend.agendamiento.domain.Cita
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

data class ReservaCreateRequest(
    @field:NotNull
    val servicioId: UUID,
    @field:NotNull
    val mascotaId: UUID,
    @field:NotNull
    val fechaHoraInicio: LocalDateTime,
    @field:NotNull
    val origen: OrigenCita
)

data class CitaResponse(
    val id: UUID,
    val fechaHoraInicio: LocalDateTime,
    val fechaHoraFin: LocalDateTime,
    val estado: EstadoCita,
    val precioFinal: Int,
    val servicioId: UUID,
    val mascotaId: UUID,
    val tutorId: UUID,
    val origen: OrigenCita,
    val paymentUrl: String?
)

fun Cita.toResponse(paymentUrl: String? = null) = CitaResponse(
    id = id!!,
    fechaHoraInicio = fechaHoraInicio,
    fechaHoraFin = fechaHoraFin,
    estado = estado,
    precioFinal = precioFinal,
    servicioId = servicioId,
    mascotaId = mascotaId,
    tutorId = tutorId,
    origen = origen,
    paymentUrl = paymentUrl
)

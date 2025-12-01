package cl.clinipets.backend.agendamiento.api

import cl.clinipets.backend.agendamiento.domain.EstadoCita
import cl.clinipets.backend.agendamiento.domain.OrigenCita
import cl.clinipets.backend.agendamiento.domain.Cita
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class ReservaCreateRequest(
    @field:NotNull
    val servicioId: UUID,
    @field:NotNull
    val mascotaId: UUID,
    @field:NotNull
    val fechaHoraInicio: Instant,
    @field:NotNull
    val origen: OrigenCita
)

data class CitaResponse(
    val id: UUID,
    val fechaHoraInicio: Instant,
    val fechaHoraFin: Instant,
    val estado: EstadoCita,
    val precioFinal: Int,
    val servicioId: UUID,
    val mascotaId: UUID,
    val tutorId: UUID,
    val origen: OrigenCita,
    val paymentUrl: String?
)

data class CitaDetalladaResponse(
    val id: UUID,
    val fechaHoraInicio: Instant,
    val fechaHoraFin: Instant,
    val estado: EstadoCita,
    val precioFinal: Int,
    val servicioId: UUID,
    val nombreServicio: String,
    val mascotaId: UUID,
    val nombreMascota: String,
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

fun Cita.toDetalladaResponse(nombreServicio: String, nombreMascota: String, paymentUrl: String? = null) = CitaDetalladaResponse(
    id = id!!,
    fechaHoraInicio = fechaHoraInicio,
    fechaHoraFin = fechaHoraFin,
    estado = estado,
    precioFinal = precioFinal,
    servicioId = servicioId,
    nombreServicio = nombreServicio,
    mascotaId = mascotaId,
    nombreMascota = nombreMascota,
    tutorId = tutorId,
    origen = origen,
    paymentUrl = paymentUrl
)

package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.DetalleCita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.agendamiento.domain.TipoAtencion
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class DetalleReservaRequest(
    @field:NotNull
    val servicioId: UUID,
    val mascotaId: UUID? // Optional for products
)

data class ReservaCreateRequest(
    @field:NotEmpty
    @field:Valid
    val detalles: List<DetalleReservaRequest>,
    @field:NotNull
    val fechaHoraInicio: Instant,
    @field:NotNull
    val origen: OrigenCita,
    val tipoAtencion: TipoAtencion = TipoAtencion.CLINICA,
    val direccion: String? = null
)

data class DetalleCitaResponse(
    val id: UUID,
    val servicioId: UUID,
    val nombreServicio: String,
    val mascotaId: UUID?,
    val nombreMascota: String?,
    val precioUnitario: Int
)

data class CitaResponse(
    val id: UUID,
    val fechaHoraInicio: Instant,
    val fechaHoraFin: Instant,
    val estado: EstadoCita,
    val precioFinal: Int,
    val montoAbono: Int,
    val saldoPendiente: Int,
    val detalles: List<DetalleCitaResponse>,
    val tutorId: UUID,
    val origen: OrigenCita,
    val tipoAtencion: TipoAtencion,
    val direccion: String?,
    val paymentUrl: String?
)

data class CitaDetalladaResponse(
    val id: UUID,
    val fechaHoraInicio: Instant,
    val fechaHoraFin: Instant,
    val estado: EstadoCita,
    val precioFinal: Int,
    val montoAbono: Int,
    val saldoPendiente: Int,
    val detalles: List<DetalleCitaResponse>,
    val tutorId: UUID,
    val origen: OrigenCita,
    val tipoAtencion: TipoAtencion,
    val direccion: String?,
    val paymentUrl: String?
)

fun Cita.toResponse(paymentUrl: String? = null) = CitaResponse(
    id = id!!,
    fechaHoraInicio = fechaHoraInicio,
    fechaHoraFin = fechaHoraFin,
    estado = estado,
    precioFinal = precioFinal,
    montoAbono = montoAbono,
    saldoPendiente = precioFinal - montoAbono,
    detalles = detalles.map { it.toResponse() },
    tutorId = tutorId,
    origen = origen,
    tipoAtencion = tipoAtencion,
    direccion = direccion,
    paymentUrl = paymentUrl
)

fun Cita.toDetalladaResponse(paymentUrl: String? = null) = CitaDetalladaResponse(
    id = id!!,
    fechaHoraInicio = fechaHoraInicio,
    fechaHoraFin = fechaHoraFin,
    estado = estado,
    precioFinal = precioFinal,
    montoAbono = montoAbono,
    saldoPendiente = precioFinal - montoAbono,
    detalles = detalles.map { it.toResponse() },
    tutorId = tutorId,
    origen = origen,
    tipoAtencion = tipoAtencion,
    direccion = direccion,
    paymentUrl = paymentUrl
)

fun DetalleCita.toResponse() = DetalleCitaResponse(
    id = id!!,
    servicioId = servicio.id!!,
    nombreServicio = servicio.nombre,
    mascotaId = mascota?.id,
    nombreMascota = mascota?.nombre,
    precioUnitario = precioUnitario
)
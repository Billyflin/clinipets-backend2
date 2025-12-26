package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.DetalleCita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.agendamiento.domain.TipoAtencion
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class DetalleReservaRequest(
    @field:NotNull
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ID del servicio médico o producto")
    val servicioId: UUID,

    @field:Schema(description = "ID de la mascota (opcional para productos)")
    val mascotaId: UUID? // Optional for products
)

data class ReservaItemRequest(
    @field:NotNull
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val mascotaId: UUID,

    @field:NotNull
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val servicioId: UUID,

    @field:Schema(description = "Cantidad del servicio (opcional, por defecto 1)")
    val cantidad: Int = 1
)

data class ReservaCreateRequest(
    @field:NotEmpty
    @field:Valid
    @field:Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Lista de servicios para las mascotas"
    )
    val detalles: List<ReservaItemRequest>,

    @field:NotNull
    @field:Future
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Fecha y hora de inicio de la cita")
    val fechaHoraInicio: Instant,

    @field:Schema(description = "Motivo de la consulta")
    val motivoConsulta: String? = null,

    @field:NotNull
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Origen de la cita (APP, WEB, PRESENCIAL)")
    val origen: OrigenCita,

    @field:Schema(description = "Tipo de atención (CLINICA, DOMICILIO)")
    val tipoAtencion: TipoAtencion = TipoAtencion.CLINICA,

    @field:Schema(description = "Dirección para atención a domicilio")
    val direccion: String? = null,

    @field:Schema(description = "Indica si el pago es total o parcial (seña)")
    val pagoTotal: Boolean = false
)

data class DetalleCitaResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val id: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val servicioId: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val nombreServicio: String,
    val mascotaId: UUID?,
    val nombreMascota: String?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val precioUnitario: BigDecimal
)

data class ResumenDiarioResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val totalCitas: Int,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val citasFinalizadas: Int,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val recaudacionTotalRealizada: BigDecimal,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val proyeccionPendiente: BigDecimal,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val desgloseMetodosPago: Map<cl.clinipets.agendamiento.domain.MetodoPago, Int>,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val totalGeneral: BigDecimal
)

data class CitaResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val id: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val fechaHoraInicio: Instant,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val fechaHoraFin: Instant,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val estado: EstadoCita,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val precioFinal: BigDecimal,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val saldoPendiente: BigDecimal,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val detalles: List<DetalleCitaResponse>,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val tutorId: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val origen: OrigenCita,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val tipoAtencion: TipoAtencion,
    val motivoConsulta: String?,
    val direccion: String?
)

data class CitaDetalladaResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val id: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val fechaHoraInicio: Instant,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val fechaHoraFin: Instant,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val estado: EstadoCita,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val precioFinal: BigDecimal,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val saldoPendiente: BigDecimal,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val detalles: List<DetalleCitaResponse>,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val tutorId: UUID,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val origen: OrigenCita,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val tipoAtencion: TipoAtencion,
    val motivoConsulta: String?,
    val direccion: String?
)

fun Cita.toResponse() = CitaResponse(
    id = id!!,
    fechaHoraInicio = fechaHoraInicio,
    fechaHoraFin = fechaHoraFin,
    estado = estado,
    precioFinal = precioFinal,
    saldoPendiente = saldoPendiente(),
    detalles = detalles.map { it.toResponse() },
    tutorId = tutor.id!!,
    origen = origen,
    tipoAtencion = tipoAtencion,
    motivoConsulta = motivoConsulta,
    direccion = direccion
)

fun Cita.toDetalladaResponse() = CitaDetalladaResponse(
    id = id!!,
    fechaHoraInicio = fechaHoraInicio,
    fechaHoraFin = fechaHoraFin,
    estado = estado,
    precioFinal = precioFinal,
    saldoPendiente = when (estado) {
        EstadoCita.CANCELADA -> BigDecimal.ZERO
        else -> saldoPendiente()
    },
    detalles = detalles.map { it.toResponse() },
    tutorId = tutor.id!!,
    origen = origen,
    tipoAtencion = tipoAtencion,
    motivoConsulta = motivoConsulta,
    direccion = direccion
)

fun DetalleCita.toResponse() = DetalleCitaResponse(
    id = id!!,
    servicioId = servicio.id!!,
    nombreServicio = servicio.nombre,
    mascotaId = mascota?.id,
    nombreMascota = mascota?.nombre,
    precioUnitario = precioUnitario
)

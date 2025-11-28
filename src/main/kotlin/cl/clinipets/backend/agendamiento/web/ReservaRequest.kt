package cl.clinipets.backend.agendamiento.web

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class ReservaRequest(
    val mascotaId: UUID? = null,

    @field:NotNull(message = "El ID del servicio es obligatorio")
    val servicioId: Long,

    @field:NotNull(message = "La fecha y hora son obligatorias")
    @field:Future(message = "La fecha de la cita debe ser en el futuro")
    val fechaHora: LocalDateTime,

    val pesoActual: BigDecimal? = null,

    val telefono: String? = null,
    val nombreContacto: String? = null
)

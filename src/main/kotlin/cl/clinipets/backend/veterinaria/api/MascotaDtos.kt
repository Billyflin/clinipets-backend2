package cl.clinipets.backend.veterinaria.api

import cl.clinipets.backend.veterinaria.domain.Especie
import cl.clinipets.backend.veterinaria.domain.Mascota
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class MascotaCreateRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    val nombre: String,
    @field:NotNull(message = "La especie es obligatoria")
    val especie: Especie,
    @field:NotNull(message = "El peso es obligatorio")
    val pesoActual: BigDecimal,
    @field:NotNull(message = "La fecha de nacimiento es obligatoria")
    val fechaNacimiento: Instant
)

data class MascotaResponse(
    val id: UUID,
    val nombre: String,
    val especie: Especie,
    val pesoActual: BigDecimal,
    val fechaNacimiento: Instant,
    val tutorId: UUID
)

fun Mascota.toResponse() = MascotaResponse(
    id = id!!,
    nombre = nombre,
    especie = especie,
    pesoActual = pesoActual,
    fechaNacimiento = fechaNacimiento,
    tutorId = tutor.id!!
)

package cl.clinipets.veterinaria.api

import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.veterinaria.domain.Temperamento
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class MascotaCreateRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    val nombre: String,
    @field:NotNull(message = "La especie es obligatoria")
    val especie: Especie,
    @field:NotBlank(message = "La raza es obligatoria (o 'Mestizo')")
    val raza: String,
    @field:NotNull(message = "El sexo es obligatorio")
    val sexo: Sexo,
    val esterilizado: Boolean = false,
    val chipIdentificador: String? = null,
    val temperamento: Temperamento = Temperamento.DOCIL,
    val pesoActual: BigDecimal? = null,
    val fechaNacimiento: LocalDate? = null
)

data class MascotaUpdateRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    val nombre: String,
    @field:NotNull(message = "El peso es obligatorio")
    val pesoActual: BigDecimal,
    val raza: String?,
    val sexo: Sexo?,
    val esterilizado: Boolean?,
    val chipIdentificador: String?,
    val temperamento: Temperamento?
)

data class MascotaResponse(
    val id: UUID,
    val nombre: String,
    val especie: Especie,
    val raza: String,
    val sexo: Sexo,
    val esterilizado: Boolean,
    val chipIdentificador: String?,
    val temperamento: Temperamento,
    val pesoActual: BigDecimal,
    val fechaNacimiento: LocalDate,
    val tutorId: UUID
)

fun Mascota.toResponse() = MascotaResponse(
    id = id!!,
    nombre = nombre,
    especie = especie,
    raza = raza,
    sexo = sexo,
    esterilizado = esterilizado,
    chipIdentificador = chipIdentificador,
    temperamento = temperamento,
    pesoActual = pesoActual,
    fechaNacimiento = fechaNacimiento,
    tutorId = tutor.id!!
)

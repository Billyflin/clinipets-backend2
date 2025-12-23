package cl.clinipets.veterinaria.api

import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.veterinaria.domain.Temperamento
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class MascotaCreateRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val nombre: String,

    @field:NotNull(message = "La especie es obligatoria")
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val especie: Especie,

    @field:NotBlank(message = "La raza es obligatoria (o 'Mestizo')")
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val raza: String,

    @field:NotNull(message = "El sexo es obligatorio")
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val sexo: Sexo,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val esterilizado: Boolean = false,

    val chipIdentificador: String? = null,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val temperamento: Temperamento = Temperamento.DOCIL,

    val pesoActual: Double? = null,
    val fechaNacimiento: LocalDate? = null
)

data class MascotaUpdateRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val nombre: String,

    @field:NotNull(message = "El peso es obligatorio")
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val pesoActual: Double,
    
    val raza: String?,
    val sexo: Sexo?,
    val esterilizado: Boolean?,
    val chipIdentificador: String?,
    val temperamento: Temperamento?
)

data class MascotaClinicalUpdateRequest(
    val pesoActual: Double?,
    val esterilizado: Boolean?,
    val testRetroviralNegativo: Boolean?,
    val observaciones: String?
)

data class MascotaResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val id: UUID,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val nombre: String,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val especie: Especie,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val raza: String,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val sexo: Sexo,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val esterilizado: Boolean,

    val chipIdentificador: String?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val temperamento: Temperamento,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5.5")
    val pesoActual: Double,

    val fechaNacimiento: LocalDate?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val tutorId: UUID,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val testRetroviralNegativo: Boolean,

    val fechaUltimoTestRetroviral: LocalDate?,
    val observacionesClinicas: String?
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
    tutorId = tutor.id!!,
    testRetroviralNegativo = testRetroviralNegativo,
    fechaUltimoTestRetroviral = fechaUltimoTestRetroviral,
    observacionesClinicas = observacionesClinicas
)

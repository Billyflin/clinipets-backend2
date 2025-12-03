package cl.clinipets.veterinaria.historial.api

import cl.clinipets.veterinaria.historial.domain.FichaClinica
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class FichaCreateRequest(
    @field:NotNull(message = "El ID de la mascota es obligatorio")
    val mascotaId: UUID,
    val fechaAtencion: Instant = Instant.now(),
    @field:NotBlank(message = "El motivo de consulta es obligatorio")
    val motivoConsulta: String,
    val observaciones: String? = null,
    val diagnostico: String? = null,
    val esVacuna: Boolean = false,
    val nombreVacuna: String? = null,
    val fechaProximaDosis: LocalDate? = null
)

data class FichaResponse(
    val id: UUID,
    val mascotaId: UUID,
    val fechaAtencion: Instant,
    val motivoConsulta: String,
    val observaciones: String?,
    val diagnostico: String?,
    val esVacuna: Boolean,
    val nombreVacuna: String?,
    val fechaProximaDosis: LocalDate?,
    val autorId: UUID
)

fun FichaClinica.toResponse() = FichaResponse(
    id = id!!,
    mascotaId = mascota.id!!,
    fechaAtencion = fechaAtencion,
    motivoConsulta = motivoConsulta,
    observaciones = observaciones,
    diagnostico = diagnostico,
    esVacuna = esVacuna,
    nombreVacuna = nombreVacuna,
    fechaProximaDosis = fechaProximaDosis,
    autorId = autorId
)

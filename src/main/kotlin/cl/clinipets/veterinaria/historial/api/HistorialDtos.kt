package cl.clinipets.veterinaria.historial.api

import cl.clinipets.veterinaria.historial.domain.FichaClinica
import jakarta.validation.constraints.Future
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
    val anamnesis: String? = null,
    val examenFisico: String? = null,
    val tratamiento: String? = null,
    val pesoRegistrado: Double? = null,
    val observaciones: String? = null,
    val diagnostico: String? = null,
    val esVacuna: Boolean = false,
    val nombreVacuna: String? = null,
    @field:Future
    val fechaProximaVacuna: LocalDate? = null,
    @field:Future
    val fechaProximoControl: LocalDate? = null,
    val fechaDesparasitacion: LocalDate? = null
)

data class FichaResponse(
    val id: UUID,
    val mascotaId: UUID,
    val fechaAtencion: Instant,
    val motivoConsulta: String,
    val anamnesis: String?,
    val examenFisico: String?,
    val tratamiento: String?,
    val pesoRegistrado: Double?,
    val observaciones: String?,
    val diagnostico: String?,
    val esVacuna: Boolean,
    val nombreVacuna: String?,
    val fechaProximaVacuna: LocalDate?,
    val fechaProximoControl: LocalDate?,
    val fechaDesparasitacion: LocalDate?,
    val autorId: UUID
)

fun FichaClinica.toResponse() = FichaResponse(
    id = id!!,
    mascotaId = mascota.id!!,
    fechaAtencion = fechaAtencion,
    motivoConsulta = motivoConsulta,
    anamnesis = anamnesis,
    examenFisico = examenFisico,
    tratamiento = tratamiento,
    pesoRegistrado = pesoRegistrado,
    observaciones = observaciones,
    diagnostico = diagnostico,
    esVacuna = esVacuna,
    nombreVacuna = nombreVacuna,
    fechaProximaVacuna = fechaProximaVacuna,
    fechaProximoControl = fechaProximoControl,
    fechaDesparasitacion = fechaDesparasitacion,
    autorId = autorId
)

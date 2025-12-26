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
    val citaId: UUID? = null,
    val fechaAtencion: Instant = Instant.now(),
    @field:NotBlank(message = "El motivo de consulta es obligatorio")
    val motivoConsulta: String,
    
    // SOAP
    val anamnesis: String? = null,
    val hallazgosObjetivos: String? = null,
    val avaluoClinico: String? = null,
    val planTratamiento: String? = null,
    
    // Constantes Vitales
    val pesoRegistrado: Double? = null,
    val temperatura: Double? = null,
    val frecuenciaCardiaca: Int? = null,
    val frecuenciaRespiratoria: Int? = null,
    
    val observaciones: String? = null,
    val esVacuna: Boolean = false,
    val nombreVacuna: String? = null,
    @field:Future
    val fechaProximaVacuna: LocalDate? = null,
    @field:Future
    val fechaProximoControl: LocalDate? = null,
    val fechaDesparasitacion: LocalDate? = null
)

data class FichaUpdateRequest(
    val anamnesis: String? = null,
    val hallazgosObjetivos: String? = null,
    val avaluoClinico: String? = null,
    val planTratamiento: String? = null,
    
    // Constantes Vitales
    val pesoRegistrado: Double? = null,
    val temperatura: Double? = null,
    val frecuenciaCardiaca: Int? = null,
    val frecuenciaRespiratoria: Int? = null,
    
    val observaciones: String? = null,
    val fechaProximaVacuna: LocalDate? = null,
    val fechaProximoControl: LocalDate? = null,
    val fechaDesparasitacion: LocalDate? = null
)

data class FichaResponse(
    val id: UUID,
    val mascotaId: UUID,
    val citaId: UUID?,
    val fechaAtencion: Instant,
    val motivoConsulta: String,
    val anamnesis: String?,
    val hallazgosObjetivos: String?,
    val avaluoClinico: String?,
    val planTratamiento: String?,
    val pesoRegistrado: Double?,
    val temperatura: Double?,
    val frecuenciaCardiaca: Int?,
    val frecuenciaRespiratoria: Int?,
    val alertaVeterinaria: Boolean,
    val observaciones: String?,
    val esVacuna: Boolean,
    val nombreVacuna: String?,
    val fechaProximaVacuna: LocalDate?,
    val fechaProximoControl: LocalDate?,
    val fechaDesparasitacion: LocalDate?,
    val autorId: UUID
)

data class PesoPunto(
    val fecha: Instant,
    val peso: Double
)

data class PesoHistoryResponse(
    val mascotaId: UUID,
    val puntos: List<PesoPunto>
)

fun FichaClinica.toResponse() = FichaResponse(
    id = id!!,
    mascotaId = mascota.id!!,
    citaId = cita?.id,
    fechaAtencion = fechaAtencion,
    motivoConsulta = motivoConsulta,
    anamnesis = anamnesis,
    hallazgosObjetivos = hallazgosObjetivos,
    avaluoClinico = avaluoClinico,
    planTratamiento = planTratamiento,
    pesoRegistrado = signosVitales.pesoRegistrado,
    temperatura = signosVitales.temperatura,
    frecuenciaCardiaca = signosVitales.frecuenciaCardiaca,
    frecuenciaRespiratoria = signosVitales.frecuenciaRespiratoria,
    alertaVeterinaria = signosVitales.alertaVeterinaria,
    observaciones = observaciones,
    esVacuna = planSanitario.esVacuna,
    nombreVacuna = planSanitario.nombreVacuna,
    fechaProximaVacuna = planSanitario.fechaProximaVacuna,
    fechaProximoControl = planSanitario.fechaProximoControl,
    fechaDesparasitacion = planSanitario.fechaDesparasitacion,
    autorId = autor.id!!
)
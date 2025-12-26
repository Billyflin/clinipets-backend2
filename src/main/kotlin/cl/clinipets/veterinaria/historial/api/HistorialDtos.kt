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
    val pesoActual: Double? = null,
    
    // SOAP Structured
    val anamnesis: String? = null,
    val examenFisico: ExamenFisicoDto? = null,
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
    val fechaDesparasitacion: LocalDate? = null,
    val marcadores: Map<String, String>? = null,
    val recetas: List<RecetaRequest> = emptyList()
)

data class FichaUpdateRequest(
    val anamnesis: String? = null,
    val examenFisico: ExamenFisicoDto? = null,
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
    val fechaDesparasitacion: LocalDate? = null,
    val marcadores: Map<String, String>? = null,
    val recetas: List<RecetaRequest>? = null
)

data class ExamenFisicoDto(
    val mucosas: String? = null,
    val tllc: String? = null,
    val hidratacion: String? = null,
    val linfonodos: String? = null,
    val pielAnexos: String? = null,
    val sistemaCardiovascular: String? = null,
    val sistemaRespiratorio: String? = null,
    val sistemaDigestivo: String? = null,
    val sistemaGenitourinario: String? = null,
    val sistemaNervioso: String? = null,
    val sistemaOsteoarticular: String? = null
)

data class FichaResponse(
    val id: UUID,
    val mascotaId: UUID,
    val citaId: UUID?,
    val fechaAtencion: Instant,
    val motivoConsulta: String,
    val anamnesis: String?,
    val examenFisico: ExamenFisicoDto?,
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
    val autorId: UUID,
    val recetas: List<RecetaResponse> = emptyList()
)

data class RecetaRequest(
    val items: List<ItemPrescripcionRequest>,
    val observaciones: String? = null
)

data class ItemPrescripcionRequest(
    val insumoId: UUID,
    val dosis: String,
    val frecuencia: String,
    val duracion: String,
    val cantidadADespachar: Double
)

data class RecetaResponse(
    val id: UUID,
    val items: List<ItemPrescripcionResponse>,
    val observaciones: String?
)

data class ItemPrescripcionResponse(
    val id: UUID,
    val insumoId: UUID,
    val nombreInsumo: String,
    val dosis: String,
    val frecuencia: String,
    val duracion: String,
    val cantidadADespachar: Double
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

    examenFisico = examenFisico.toDto(),

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

        autorId = autor.id!!,

        recetas = recetas.map { it.toResponse() }

    )

    

    fun cl.clinipets.veterinaria.historial.domain.RecetaMedica.toResponse() = RecetaResponse(

        id = id!!,

        items = items.map { it.toResponse() },

        observaciones = observaciones

    )

    

    fun cl.clinipets.veterinaria.historial.domain.ItemPrescripcion.toResponse() = ItemPrescripcionResponse(

        id = id!!,

        insumoId = insumo.id!!,

        nombreInsumo = insumo.nombre,

        dosis = dosis,

        frecuencia = frecuencia,

        duracion = duracion,

        cantidadADespachar = cantidadADespachar

    )

    

    fun cl.clinipets.veterinaria.historial.domain.ExamenFisico.toDto() = ExamenFisicoDto(

    

    mucosas = mucosas,

    tllc = tllc,

    hidratacion = hidratacion,

    linfonodos = linfonodos,

    pielAnexos = pielAnexos,

    sistemaCardiovascular = sistemaCardiovascular,

    sistemaRespiratorio = sistemaRespiratorio,

    sistemaDigestivo = sistemaDigestivo,

    sistemaGenitourinario = sistemaGenitourinario,

    sistemaNervioso = sistemaNervioso,

    sistemaOsteoarticular = sistemaOsteoarticular

)



fun ExamenFisicoDto.toEntity() = cl.clinipets.veterinaria.historial.domain.ExamenFisico(

    mucosas = mucosas,

    tllc = tllc,

    hidratacion = hidratacion,

    linfonodos = linfonodos,

    pielAnexos = pielAnexos,

    sistemaCardiovascular = sistemaCardiovascular,

    sistemaRespiratorio = sistemaRespiratorio,

    sistemaDigestivo = sistemaDigestivo,

    sistemaGenitourinario = sistemaGenitourinario,

    sistemaNervioso = sistemaNervioso,

    sistemaOsteoarticular = sistemaOsteoarticular

)

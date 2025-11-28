package cl.clinipets.backend.servicios.web

import cl.clinipets.backend.servicios.dominio.ReglaPrecio
import cl.clinipets.backend.servicios.dominio.ServicioMedico
import java.math.BigDecimal

data class ServicioMedicoDTO(
    val id: Long?,
    val nombre: String,
    val precioBase: Int,
    val requierePeso: Boolean,
    val esUrgencia: Boolean,
    val descripcion: String?,
    val reglasPrecio: List<ReglaPrecioDTO> = emptyList()
) {
    companion object {
        fun fromEntity(entity: ServicioMedico): ServicioMedicoDTO {
            return ServicioMedicoDTO(
                id = entity.id,
                nombre = entity.nombre,
                precioBase = entity.precioBase,
                requierePeso = entity.requierePeso,
                esUrgencia = entity.esUrgencia,
                descripcion = entity.descripcion,
                reglasPrecio = entity.reglasPrecio.map { ReglaPrecioDTO.fromEntity(it) }
            )
        }
    }
}

data class ReglaPrecioDTO(
    val id: Long?,
    val pesoMin: BigDecimal,
    val pesoMax: BigDecimal,
    val precio: Int
) {
    companion object {
        fun fromEntity(entity: ReglaPrecio): ReglaPrecioDTO {
            return ReglaPrecioDTO(
                id = entity.id,
                pesoMin = entity.pesoMin,
                pesoMax = entity.pesoMax,
                precio = entity.precio
            )
        }
    }
}

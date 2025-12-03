package cl.clinipets.servicios.api

import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Especie
import java.math.BigDecimal
import java.util.UUID

data class ReglaPrecioDto(
    val id: UUID,
    val pesoMin: BigDecimal,
    val pesoMax: BigDecimal,
    val precio: Int
)

data class ServicioMedicoDto(
    val id: UUID,
    val nombre: String,
    val precioBase: Int,
    val requierePeso: Boolean,
    val duracionMinutos: Int,
    val activo: Boolean,
    val categoria: CategoriaServicio,
    val especiesPermitidas: Set<Especie>,
    val reglas: List<ReglaPrecioDto>
)

fun ServicioMedico.toDto() = ServicioMedicoDto(
    id = id!!,
    nombre = nombre,
    precioBase = precioBase,
    requierePeso = requierePeso,
    duracionMinutos = duracionMinutos,
    activo = activo,
    categoria = categoria,
    especiesPermitidas = especiesPermitidas,
    reglas = reglas.map(ReglaPrecio::toDto)
)

fun ReglaPrecio.toDto() = ReglaPrecioDto(
    id = id!!,
    pesoMin = pesoMin,
    pesoMax = pesoMax,
    precio = precio
)

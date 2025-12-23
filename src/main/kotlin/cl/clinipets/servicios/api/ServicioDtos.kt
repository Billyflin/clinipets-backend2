package cl.clinipets.servicios.api

import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Especie
import java.math.BigDecimal
import java.util.UUID

data class ReglaPrecioDto(
    val id: UUID,
    val pesoMin: Double,
    val pesoMax: Double,
    val precio: Int
)

data class ServicioMedicoDto(
    val id: UUID,
    val nombre: String,
    val precioBase: Int,
    val precioAbono: Int?,
    val requierePeso: Boolean,
    val duracionMinutos: Int,
    val activo: Boolean,
    val categoria: CategoriaServicio,
    val especiesPermitidas: Set<Especie>,
    val stock: Int?,
    val bloqueadoSiEsterilizado: Boolean,
    val serviciosRequeridosIds: Set<UUID>,
    val reglas: List<ReglaPrecioDto>
)

fun ServicioMedico.toDto() = ServicioMedicoDto(
    id = id!!,
    nombre = nombre,
    precioBase = precioBase,
    precioAbono = precioAbono,
    requierePeso = requierePeso,
    duracionMinutos = duracionMinutos,
    activo = activo,
    categoria = categoria,
    especiesPermitidas = especiesPermitidas,
    stock = stock,
    bloqueadoSiEsterilizado = bloqueadoSiEsterilizado,
    serviciosRequeridosIds = serviciosRequeridosIds,
    reglas = reglas.map(ReglaPrecio::toDto)
)

fun ReglaPrecio.toDto() = ReglaPrecioDto(
    id = id!!,
    pesoMin = pesoMin,
    pesoMax = pesoMax,
    precio = precio
)
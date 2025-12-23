package cl.clinipets.servicios.api

import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioInsumo
import cl.clinipets.veterinaria.domain.Especie
import java.math.BigDecimal
import java.util.UUID

data class ReglaPrecioDto(
    val id: UUID,
    val pesoMin: Double,
    val pesoMax: Double,
    val precio: Int
)

data class InsumoDto(
    val id: UUID,
    val nombre: String,
    val cantidadRequerida: Double,
    val unidadMedida: String,
    val critico: Boolean
)

data class InsumoDetalladoDto(
    val id: UUID,
    val nombre: String,
    val stockActual: Double,
    val stockMinimo: Int,
    val unidadMedida: String
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
    val reglas: List<ReglaPrecioDto>,
    val insumos: List<InsumoDto>
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
    reglas = reglas.map(ReglaPrecio::toDto),
    insumos = insumos.map(ServicioInsumo::toDto)
)

fun ReglaPrecio.toDto() = ReglaPrecioDto(
    id = id!!,
    pesoMin = pesoMin,
    pesoMax = pesoMax,
    precio = precio
)

fun ServicioInsumo.toDto() = InsumoDto(

    id = insumo.id!!,

    nombre = insumo.nombre,

    cantidadRequerida = cantidadRequerida,

    unidadMedida = insumo.unidadMedida,

    critico = critico

)


fun cl.clinipets.servicios.domain.Insumo.toDetalladoDto() = InsumoDetalladoDto(

    id = id!!,

    nombre = nombre,

    stockActual = stockActual,

    stockMinimo = stockMinimo,

    unidadMedida = unidadMedida

)

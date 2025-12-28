package cl.clinipets.servicios.api

import cl.clinipets.servicios.domain.*
import cl.clinipets.veterinaria.domain.Especie
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class ReglaPrecioDto(
    val id: UUID,
    val pesoMin: Double,
    val pesoMax: Double,
    val precio: BigDecimal
)

data class ReglaPrecioRequest(
    @field:NotNull val pesoMin: Double,
    @field:NotNull val pesoMax: Double,
    @field:NotNull @field:Min(0) val precio: BigDecimal
)

data class InsumoDto(
    val id: UUID,
    val nombre: String,
    val cantidadRequerida: Double,
    val unidadMedida: String,
    val critico: Boolean
)

data class InsumoRequest(
    @field:NotNull val insumoId: UUID,
    @field:NotNull @field:Min(0) val cantidadRequerida: Double,
    @field:NotNull val critico: Boolean
)

data class InsumoDetalladoDto(
    val id: UUID,
    val nombre: String,
    val stockActual: Double,
    val stockMinimo: Double,
    val unidadMedida: String
)

data class ServicioMedicoDto(
    val id: UUID,
    val nombre: String,
    val precioBase: BigDecimal,
    val precioAbono: BigDecimal?,
    val requierePeso: Boolean,
    val duracionMinutos: Int,
    val activo: Boolean,
    val categoria: CategoriaServicio,
    val especiesPermitidas: Set<Especie>,
    val stock: Int?,
    val bloqueadoSiEsterilizado: Boolean,
    val serviciosRequeridosIds: Set<UUID>,
    val reglas: List<ReglaPrecioDto>,
    val insumos: List<InsumoDto>,
    val actualizaMarcador: String?,
    val condicionMarcadorClave: String?,
    val condicionMarcadorValor: String?
)

data class ServicioCreateRequest(
    @field:NotBlank val nombre: String,
    @field:NotNull @field:Min(0) val precioBase: BigDecimal,
    val precioAbono: BigDecimal? = null,
    @field:NotNull val requierePeso: Boolean,
    @field:NotNull @field:Min(1) val duracionMinutos: Int,
    val categoria: CategoriaServicio = CategoriaServicio.OTRO,
    val especiesPermitidas: Set<Especie> = emptySet(),
    val stock: Int? = null,
    val bloqueadoSiEsterilizado: Boolean = false,
    val serviciosRequeridosIds: Set<UUID> = emptySet(),
    val reglas: List<ReglaPrecioRequest> = emptyList(),
    val insumos: List<InsumoRequest> = emptyList(),
    val actualizaMarcador: String? = null,
    val condicionMarcadorClave: String? = null,
    val condicionMarcadorValor: String? = null
)

data class ServicioUpdateRequest(
    val nombre: String? = null,
    val precioBase: BigDecimal? = null,
    val precioAbono: BigDecimal? = null,
    val requierePeso: Boolean? = null,
    val duracionMinutos: Int? = null,
    val activo: Boolean? = null,
    val categoria: CategoriaServicio? = null,
    val especiesPermitidas: Set<Especie>? = null,
    val stock: Int? = null,
    val bloqueadoSiEsterilizado: Boolean? = null,
    val actualizaMarcador: String? = null,
    val condicionMarcadorClave: String? = null,
    val condicionMarcadorValor: String? = null
)

data class LoteDto(
    val id: UUID,
    val codigoLote: String,
    val fechaVencimiento: LocalDate,
    val cantidadInicial: Double,
    val cantidadActual: Double,
    val estaVencido: Boolean
)

data class LoteCreateRequest(
    @field:NotBlank val codigoLote: String,
    @field:NotNull val fechaVencimiento: LocalDate,
    @field:NotNull @field:Min(0) val cantidad: Double
)

data class InsumoCreateRequest(
    @field:NotBlank val nombre: String,
    @field:NotNull @field:Min(0) val stockMinimo: Double,
    @field:NotBlank val unidadMedida: String,
    val contraindicacionMarcador: String? = null
)

data class InsumoUpdateRequest(
    val nombre: String? = null,
    val stockMinimo: Double? = null,
    val unidadMedida: String? = null,
    val contraindicacionMarcador: String? = null
)

data class LoteStockAjusteRequest(
    @field:NotNull val nuevaCantidad: Double,
    @field:NotBlank val motivo: String
)

data class InsumoResponse(
    val id: UUID,
    val nombre: String,
    val stockActual: Double,
    val stockMinimo: Double,
    val unidadMedida: String,
    val contraindicacionMarcador: String?,
    val lotes: List<LoteDto>
)

data class PromocionBeneficioRequest(
    @field:NotNull val servicioId: UUID,
    @field:NotNull val tipo: TipoDescuento,
    @field:NotNull @field:Min(0) val valor: BigDecimal
)

data class PromocionBeneficioDto(
    val servicioId: UUID,
    val servicioNombre: String,
    val tipo: TipoDescuento,
    val valor: BigDecimal
)

data class PromocionCreateRequest(
    @field:NotBlank val nombre: String,
    val descripcion: String? = null,
    @field:NotNull val fechaInicio: LocalDate,
    @field:NotNull val fechaFin: LocalDate,
    val diasPermitidos: String? = null,
    val serviciosTriggerIds: Set<UUID> = emptySet(),
    val beneficios: List<PromocionBeneficioRequest> = emptyList()
)

data class PromocionUpdateRequest(
    val nombre: String? = null,
    val descripcion: String? = null,
    val fechaInicio: LocalDate? = null,
    val fechaFin: LocalDate? = null,
    val diasPermitidos: String? = null,
    val activa: Boolean? = null
)

data class PromocionResponse(
    val id: UUID,
    val nombre: String,
    val descripcion: String?,
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,
    val diasPermitidos: String?,
    val activa: Boolean,
    val serviciosTrigger: List<UUID>,
    val beneficios: List<PromocionBeneficioDto>
)

fun Promocion.toResponse() = PromocionResponse(
    id = id!!,
    nombre = nombre,
    descripcion = descripcion,
    fechaInicio = fechaInicio,
    fechaFin = fechaFin,
    diasPermitidos = diasPermitidos,
    activa = activa,
    serviciosTrigger = serviciosTrigger.map { it.id!! },
    beneficios = beneficios.map { it.toDto() }
)

fun PromocionBeneficio.toDto() = PromocionBeneficioDto(
    servicioId = servicio.id!!,
    servicioNombre = servicio.nombre,
    tipo = tipo,
    valor = valor
)

fun Insumo.toResponse() = InsumoResponse(
    id = id!!,
    nombre = nombre,
    stockActual = stockActual,
    stockMinimo = stockMinimo,
    unidadMedida = unidadMedida,
    contraindicacionMarcador = contraindicacionMarcador,
    lotes = lotes.map { it.toDto() }
)

fun LoteInsumo.toDto() = LoteDto(
    id = id!!,
    codigoLote = codigoLote,
    fechaVencimiento = fechaVencimiento,
    cantidadInicial = cantidadInicial,
    cantidadActual = cantidadActual,
    estaVencido = estaVencido()
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
    serviciosRequeridosIds = serviciosRequeridos.mapNotNull { it.id }.toSet(),
    reglas = reglas.map(ReglaPrecio::toDto),
    insumos = insumos.map(ServicioInsumo::toDto),
    actualizaMarcador = actualizaMarcador,
    condicionMarcadorClave = condicionMarcadorClave,
    condicionMarcadorValor = condicionMarcadorValor
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

fun Insumo.toDetalladoDto() = InsumoDetalladoDto(
    id = id!!,
    nombre = nombre,
    stockActual = stockActual,
    stockMinimo = stockMinimo,
    unidadMedida = unidadMedida
)

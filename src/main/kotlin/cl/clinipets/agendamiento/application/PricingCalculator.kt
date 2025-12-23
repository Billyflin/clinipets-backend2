package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.servicios.application.PromoEngineService
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Mascota
import org.springframework.stereotype.Service
import java.time.LocalDate

data class PrecioCalculado(
    val precioFinal: Int,
    val precioOriginal: Int,
    val descuentoAplicado: Boolean,
    val notas: List<String>
)

@Service
class PricingCalculator(
    private val promoEngineService: PromoEngineService
) {

    fun calcularPrecioFinal(servicio: ServicioMedico, mascota: Mascota?, fecha: LocalDate): PrecioCalculado {
        val servicioId =
            servicio.id ?: throw IllegalArgumentException("Servicio sin ID no permitido para cálculo de precio")

        val precioBase = when {
            servicio.categoria == CategoriaServicio.PRODUCTO -> servicio.precioBase
            mascota == null -> servicio.precioBase
            servicio.requierePeso -> servicio.calcularPrecioPara(mascota)
            else -> servicio.precioBase
        }

        val detalleRequest = DetalleReservaRequest(servicioId, mascota?.id)
        val descuentos = promoEngineService.calcularDescuentos(listOf(detalleRequest), fecha)
        val detallePromo = descuentos[servicioId]

        val deltaDescuento = detallePromo
            ?.let { (it.precioOriginal - it.precioFinal).coerceAtLeast(0) }
            ?: 0

        val precioFinal = (precioBase - deltaDescuento).coerceAtLeast(0)

        return PrecioCalculado(
            precioFinal = precioFinal,
            precioOriginal = precioBase,
            descuentoAplicado = deltaDescuento > 0,
            notas = detallePromo?.notas?.toList() ?: emptyList()
        )
    }
}

package cl.clinipets.servicios.application

import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.servicios.domain.PromocionRepository
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.servicios.domain.TipoDescuento
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class DetalleCalculado(
    val servicioId: UUID,
    var precioFinal: BigDecimal,
    val precioOriginal: BigDecimal,
    val notas: MutableList<String> = mutableListOf()
)

@Service
class PromoEngineService(
    private val promocionRepository: PromocionRepository,
    private val servicioMedicoRepository: ServicioMedicoRepository
) {

    fun calcularDescuentos(
        detalles: List<DetalleReservaRequest>,
        fechaCita: LocalDate
    ): Map<UUID, DetalleCalculado> {
        // 1. Cargar datos base de los servicios para tener los precios originales
        val serviciosIds = detalles.map { it.servicioId }.toSet()
        val serviciosInfo = servicioMedicoRepository.findAllById(serviciosIds)
            .associateBy { it.id!! }

        // Inicializar resultados con precios originales
        val resultado = detalles.associate { req ->
            val servicio = serviciosInfo[req.servicioId]
                ?: throw IllegalArgumentException("Servicio no encontrado: ${req.servicioId}")

            // Nota: Aquí estamos usando precioBase. Si el servicio requiere peso y cálculo complejo,
            // idealmente deberíamos recibir el precio ya calculado o calcularlo aquí si tuviéramos la mascota.
            // Para este MVP de motor de promociones, usaremos precioBase como punto de partida.
            val precioBase = servicio.precioBase

            req.servicioId to DetalleCalculado(
                servicioId = req.servicioId,
                precioFinal = precioBase,
                precioOriginal = precioBase
            )
        }.toMutableMap()

        // 2. Cargar promociones activas
        val promociones = promocionRepository.findAllByActivaTrue()

        // 3. Evaluar cada promoción
        promociones.forEach { promo ->
            // Filtro 1: Vigencia (Fecha y Días)
            if (!promo.estaVigente(fechaCita)) return@forEach

            // Filtro 2: Triggers (El carrito tiene todos los servicios requeridos?)
            val triggersCumplidos = if (promo.serviciosTrigger.isEmpty()) {
                true // Si no pide triggers, se aplica "siempre" (o a los items que coincidan con beneficios)
            } else {
                promo.serviciosTrigger.all { trigger ->
                    detalles.any { it.servicioId == trigger.id }
                }
            }

            if (triggersCumplidos) {
                // Aplicar beneficios
                promo.beneficios.forEach { beneficio ->
                    // Buscar si el servicio beneficiado está en el carrito (resultado)
                    val detalle = resultado[beneficio.servicio.id]
                    if (detalle != null) {
                        aplicarBeneficio(detalle, beneficio.tipo, beneficio.valor, promo.nombre)
                    }
                }
            }
        }

        return resultado
    }

    private fun aplicarBeneficio(
        detalle: DetalleCalculado,
        tipo: TipoDescuento,
        valor: BigDecimal,
        nombrePromo: String
    ) {
        val precioActual = detalle.precioFinal
        var nuevoPrecio = precioActual

        when (tipo) {
            TipoDescuento.PRECIO_FIJO -> {
                // Fija el precio al valor indicado
                nuevoPrecio = valor
            }

            TipoDescuento.MONTO_OFF -> {
                // Resta el monto
                nuevoPrecio = precioActual.subtract(valor)
            }

            TipoDescuento.PORCENTAJE_OFF -> {
                // Resta porcentaje (ej: 10% -> precio - (precio * 0.10))
                val descuento = precioActual.multiply(valor).divide(BigDecimal(100))
                nuevoPrecio = precioActual.subtract(descuento)
            }
        }

        // Validación: Precio no negativo
        if (nuevoPrecio < BigDecimal.ZERO) nuevoPrecio = BigDecimal.ZERO

        // Si hubo cambio, guardamos
        if (nuevoPrecio.compareTo(precioActual) != 0) {
            detalle.precioFinal = nuevoPrecio
            detalle.notas.add("Promo: $nombrePromo")
        }
    }
}

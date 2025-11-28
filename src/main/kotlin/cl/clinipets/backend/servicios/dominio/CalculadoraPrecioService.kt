package cl.clinipets.backend.servicios.dominio

import cl.clinipets.backend.servicios.dominio.excepciones.PesoRequeridoException
import cl.clinipets.backend.servicios.dominio.excepciones.PrecioNoDefinidoException
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class CalculadoraPrecioService {

    fun calcularPrecio(servicio: ServicioMedico, pesoMascota: BigDecimal?): Int {
        if (!servicio.requierePeso) {
            return servicio.precioBase
        }

        if (pesoMascota == null) {
            throw PesoRequeridoException()
        }

        val regla = servicio.reglasPrecio.find { regla ->
            pesoMascota >= regla.pesoMin && pesoMascota <= regla.pesoMax
        }
            ?: throw PrecioNoDefinidoException("No se encontró precio para el peso ${pesoMascota}kg en el servicio ${servicio.nombre}")

        return regla.precio
    }
}

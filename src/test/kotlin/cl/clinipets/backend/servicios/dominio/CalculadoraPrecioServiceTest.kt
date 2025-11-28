package cl.clinipets.backend.servicios.dominio

import cl.clinipets.backend.servicios.dominio.excepciones.PesoRequeridoException
import cl.clinipets.backend.servicios.dominio.excepciones.PrecioNoDefinidoException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CalculadoraPrecioServiceTest {

    private val calculadora = CalculadoraPrecioService()

    @Test
    fun `debe retornar precio base si no requiere peso`() {
        val servicio = ServicioMedico(nombre = "Consulta", precioBase = 10000, requierePeso = false)
        val precio = calculadora.calcularPrecio(servicio, null)
        assertEquals(10000, precio)
    }

    @Test
    fun `debe lanzar excepcion si requiere peso y peso es nulo`() {
        val servicio = ServicioMedico(nombre = "Cirugía", precioBase = 0, requierePeso = true)
        assertThrows(PesoRequeridoException::class.java) {
            calculadora.calcularPrecio(servicio, null)
        }
    }

    @Test
    fun `debe calcular precio segun rango de peso`() {
        val servicio = ServicioMedico(nombre = "Cirugía", precioBase = 0, requierePeso = true)
        servicio.agregarRegla(ReglaPrecio(pesoMin = BigDecimal("0.0"), pesoMax = BigDecimal("10.0"), precio = 20000))
        servicio.agregarRegla(ReglaPrecio(pesoMin = BigDecimal("10.1"), pesoMax = BigDecimal("20.0"), precio = 30000))

        assertEquals(20000, calculadora.calcularPrecio(servicio, BigDecimal("5.0")))
        assertEquals(20000, calculadora.calcularPrecio(servicio, BigDecimal("10.0"))) // Borde superior
        assertEquals(30000, calculadora.calcularPrecio(servicio, BigDecimal("10.1"))) // Borde inferior siguiente rango
        assertEquals(30000, calculadora.calcularPrecio(servicio, BigDecimal("20.0")))
    }

    @Test
    fun `debe lanzar excepcion si peso no esta en ningun rango`() {
        val servicio = ServicioMedico(nombre = "Cirugía", precioBase = 0, requierePeso = true)
        servicio.agregarRegla(ReglaPrecio(pesoMin = BigDecimal("0.0"), pesoMax = BigDecimal("10.0"), precio = 20000))

        assertThrows(PrecioNoDefinidoException::class.java) {
            calculadora.calcularPrecio(servicio, BigDecimal("15.0"))
        }
    }
}

package cl.clinipets.backend.agendamiento.domain

import cl.clinipets.agendamiento.application.PrecioCalculado
import cl.clinipets.agendamiento.application.PricingCalculator
import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.servicios.application.DetalleCalculado
import cl.clinipets.servicios.application.PromoEngineService
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Especie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class PricingCalculatorTest {

    private val promoEngineService: PromoEngineService = mock()
    private val calculator = PricingCalculator(promoEngineService)

    @Test
    fun `calcula precio base sin descuentos`() {
        val servicioId = UUID.randomUUID()
        val servicio = ServicioMedico(
            id = servicioId,
            nombre = "Consulta General",
            precioBase = BigDecimal("1000"),
            precioAbono = BigDecimal("200"),
            requierePeso = false,
            duracionMinutos = 30,
            activo = true,
            categoria = CategoriaServicio.OTRO,
            especiesPermitidas = mutableSetOf(Especie.PERRO)
        )

        whenever(promoEngineService.calcularDescuentos(any(), any(), any())).thenReturn(
            mapOf(
                servicioId to DetalleCalculado(
                    servicioId = servicioId,
                    precioFinal = BigDecimal("1000"),
                    precioOriginal = BigDecimal("1000")
                )
            )
        )

        val resultado = calculator.calcularPrecioFinal(servicio, null, LocalDate.now())

        assertEquals(
            PrecioCalculado(
                BigDecimal("1000"),
                BigDecimal("1000"),
                BigDecimal("200"),
                false,
                emptyList()
            ),
            resultado
        )
    }

    @Test
    fun `aplica descuento desde promo engine`() {
        val servicioId = UUID.randomUUID()
        val servicio = ServicioMedico(
            id = servicioId,
            nombre = "Baño Medicado",
            precioBase = BigDecimal("1000"),
            precioAbono = BigDecimal("50"),
            requierePeso = false,
            duracionMinutos = 20,
            activo = true,
            categoria = CategoriaServicio.OTRO,
            especiesPermitidas = mutableSetOf(Especie.GATO)
        )

        whenever(promoEngineService.calcularDescuentos(any(), any(), any())).thenReturn(
            mapOf(
                servicioId to DetalleCalculado(
                    servicioId = servicioId,
                    precioFinal = BigDecimal("700"),
                    precioOriginal = BigDecimal("1000"),
                    notas = mutableListOf("Promo 30%")
                )
            )
        )

        val resultado = calculator.calcularPrecioFinal(servicio, null, LocalDate.now())

        assertEquals(BigDecimal("700"), resultado.precioFinal)
        assertEquals(BigDecimal("1000"), resultado.precioOriginal)
        assertEquals(BigDecimal("50"), resultado.abono)
        assertTrue(resultado.descuentoAplicado)
        assertEquals(listOf("Promo 30%"), resultado.notas)
    }
}

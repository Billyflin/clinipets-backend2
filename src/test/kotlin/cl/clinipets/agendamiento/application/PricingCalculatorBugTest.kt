package cl.clinipets.agendamiento.application

import cl.clinipets.servicios.application.DetalleCalculado
import cl.clinipets.servicios.application.PromoEngineService
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class PricingCalculatorBugTest {

    private val promoEngineService = mock<PromoEngineService>()
    private val calculator = PricingCalculator(promoEngineService)

    @Test
    fun `el descuento porcentual deberia aplicarse sobre el precio por peso no sobre el base`() {
        // GIVEN: Un servicio con precio base 10000, pero para perros grandes (20kg) es 20000
        val servicio = ServicioMedico(
            id = UUID.randomUUID(),
            nombre = "Baño Grande",
            precioBase = BigDecimal("10000"),
            requierePeso = true,
            duracionMinutos = 60
        )
        val regla = ReglaPrecio(
            id = UUID.randomUUID(),
            pesoMin = 15.0,
            pesoMax = 100.0,
            precio = BigDecimal("20000"),
            servicio = servicio
        )
        servicio.reglas.add(regla)

        val tutor =
            User(id = UUID.randomUUID(), email = "t@t.com", name = "T", passwordHash = "h", role = UserRole.CLIENT)
        val mascota = Mascota(
            id = UUID.randomUUID(),
            nombre = "Rex",
            especie = Especie.PERRO,
            pesoActual = 20.0,
            sexo = Sexo.MACHO,
            fechaNacimiento = LocalDate.now(),
            tutor = tutor
        )

        // Simular una promo del 10%
        // Ahora pasamos 20000 como base, por lo que el mock debe responder acorde.
        val promoResult = DetalleCalculado(
            servicioId = servicio.id!!,
            precioOriginal = BigDecimal("20000"),
            precioFinal = BigDecimal("18000"),
            notas = mutableListOf("10% OFF")
        )

        whenever(
            promoEngineService.calcularDescuentos(
                any(),
                any(),
                any()
            )
        ).thenReturn(mapOf(servicio.id!! to promoResult))

        // WHEN
        val resultado = calculator.calcularPrecioFinal(servicio, mascota, LocalDate.now())

        // EXPECTATION: 
        // Si el precio es 20000 (por peso), el 10% OFF deberia ser 2000, precio final = 18000.

        println("Precio Final Calculado: ${resultado.precioFinal}")
        assertEquals(BigDecimal("18000").setScale(0), resultado.precioFinal.setScale(0))

        // Verificar que se llamo con el precio de 20000 (basado en peso) y no el base de 10000
        verify(promoEngineService).calcularDescuentos(
            any(),
            any(),
            argThat { this[servicio.id!!] == BigDecimal("20000") })
    }
}

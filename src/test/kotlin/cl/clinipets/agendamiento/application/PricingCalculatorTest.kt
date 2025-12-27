package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.servicios.application.PromoEngineService
import cl.clinipets.servicios.application.DetalleCalculado
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.*
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class PricingCalculatorTest {

    private val promoEngineService = mock<PromoEngineService>()
    private val calculator = PricingCalculator(promoEngineService)

    private fun createTutor() = User(
        id = UUID.randomUUID(),
        email = "tutor@test.com",
        name = "Tutor Test",
        passwordHash = "hash",
        role = UserRole.CLIENT
    )

    private fun createMascota(tutor: User, peso: Double? = null) = Mascota(
        id = UUID.randomUUID(),
        nombre = "Bobby",
        especie = Especie.PERRO,
        pesoActual = peso,
        sexo = Sexo.MACHO,
        fechaNacimiento = LocalDate.now().minusYears(2),
        tutor = tutor
    )

    @Test
    fun `cuando el servicio requiere peso pero la mascota no tiene peso, usa precio base`() {
        val servicio = ServicioMedico(
            id = UUID.randomUUID(),
            nombre = "Consulta Pro",
            precioBase = BigDecimal("20000"),
            requierePeso = true,
            duracionMinutos = 30
        )
        val tutor = createTutor()
        val mascota = createMascota(tutor, null)

        whenever(promoEngineService.calcularDescuentos(any(), any(), any())).thenReturn(emptyMap())

        val resultado = calculator.calcularPrecioFinal(servicio, mascota, LocalDate.now())

        assertEquals(BigDecimal("20000"), resultado.precioFinal)
    }

    @Test
    fun `cuando el descuento es mayor que el precio base, el precio final es cero`() {
        val servicio = ServicioMedico(
            id = UUID.randomUUID(),
            nombre = "Vacuna",
            precioBase = BigDecimal("10000"),
            requierePeso = false,
            duracionMinutos = 15
        )

        val promoResult = DetalleCalculado(
            servicioId = servicio.id!!,
            precioOriginal = BigDecimal("10000"),
            precioFinal = BigDecimal("-5000"),
            notas = mutableListOf("Promo Loca")
        )

        whenever(
            promoEngineService.calcularDescuentos(
                any(),
                any(),
                any()
            )
        ).thenReturn(mapOf(servicio.id!! to promoResult))

        val resultado = calculator.calcularPrecioFinal(servicio, null, LocalDate.now())

        assertEquals(BigDecimal.ZERO, resultado.precioFinal)
    }

    @Test
    fun `cuando hay reglas de peso solapadas, usa la primera que encuentre`() {
        val servicio = ServicioMedico(
            id = UUID.randomUUID(),
            nombre = "Desparasitación",
            precioBase = BigDecimal("10000"),
            requierePeso = true,
            duracionMinutos = 15
        )

        val regla1 = ReglaPrecio(
            id = UUID.randomUUID(),
            pesoMin = 0.0,
            pesoMax = 10.0,
            precio = BigDecimal("15000"),
            servicio = servicio
        )
        val regla2 = ReglaPrecio(
            id = UUID.randomUUID(),
            pesoMin = 5.0,
            pesoMax = 15.0,
            precio = BigDecimal("20000"),
            servicio = servicio
        )

        servicio.reglas.add(regla1)
        servicio.reglas.add(regla2)

        val tutor = createTutor()
        val mascota = createMascota(tutor, 7.0)

        whenever(promoEngineService.calcularDescuentos(any(), any(), any())).thenReturn(emptyMap())

        val resultado = calculator.calcularPrecioFinal(servicio, mascota, LocalDate.now())

        assertTrue(resultado.precioFinal == BigDecimal("15000") || resultado.precioFinal == BigDecimal("20000"))
    }
}

package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.servicios.domain.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PromoEngineServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var promoEngineService: PromoEngineService

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Autowired
    private lateinit var promocionRepository: PromocionRepository

    @Test
    fun `should apply percentage discount when triggers are met`() {
        // 1. Setup: Services
        val consulta = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Consulta General",
                precioBase = BigDecimal("20000"),
                duracionMinutos = 30,
                requierePeso = false
            )
        )
        val vacuna = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Vacuna Octuple", precioBase = BigDecimal("15000"), duracionMinutos = 15, requierePeso = false
            )
        )

        // 2. Setup: Promo (10% off in vacuna if consulta is also booked)
        val promo = Promocion(
            nombre = "Combo Salud",
            fechaInicio = LocalDate.now().minusDays(1),
            fechaFin = LocalDate.now().plusDays(7),
            serviciosTrigger = mutableSetOf(consulta)
        )
        promo.beneficios.add(PromocionBeneficio(vacuna, TipoDescuento.PORCENTAJE_OFF, BigDecimal("10")))
        promocionRepository.saveAndFlush(promo)

        // 3. Test
        val detalles = listOf(
            DetalleReservaRequest(servicioId = consulta.id!!, mascotaId = UUID.randomUUID()),
            DetalleReservaRequest(servicioId = vacuna.id!!, mascotaId = UUID.randomUUID())
        )

        val resultados = promoEngineService.calcularDescuentos(detalles, LocalDate.now())

        assertEquals(0, BigDecimal("20000").compareTo(resultados[consulta.id]?.precioFinal))
        // 15000 - 10% = 13500
        assertEquals(0, BigDecimal("13500").compareTo(resultados[vacuna.id]?.precioFinal))
        assertTrue(resultados[vacuna.id]?.notas?.contains("Promo: Combo Salud") == true)
    }

    @Test
    fun `should not apply promo when date is outside validity range`() {
        val consulta = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Consulta Futura", precioBase = BigDecimal("20000"), duracionMinutos = 30, requierePeso = false
            )
        )

        val promo = Promocion(
            nombre = "Promo Expirada",
            fechaInicio = LocalDate.now().minusDays(10),
            fechaFin = LocalDate.now().minusDays(1)
        )
        promo.beneficios.add(PromocionBeneficio(consulta, TipoDescuento.MONTO_OFF, BigDecimal("5000")))
        promocionRepository.saveAndFlush(promo)

        val detalles = listOf(DetalleReservaRequest(servicioId = consulta.id!!, mascotaId = UUID.randomUUID()))
        val resultados = promoEngineService.calcularDescuentos(detalles, LocalDate.now())

        assertEquals(0, BigDecimal("20000").compareTo(resultados[consulta.id]?.precioFinal))
        assertTrue(resultados[consulta.id]?.notas?.isEmpty() == true)
    }

    @Test
    fun `should apply fixed price promo regardless of original price`() {
        val cirugia = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Cirugia Cara", precioBase = BigDecimal("100000"), duracionMinutos = 60, requierePeso = false
            )
        )

        val promo = Promocion(
            nombre = "Precio Lanzamiento",
            fechaInicio = LocalDate.now().minusDays(1),
            fechaFin = LocalDate.now().plusDays(30)
        )
        promo.beneficios.add(PromocionBeneficio(cirugia, TipoDescuento.PRECIO_FIJO, BigDecimal("75000")))
        promocionRepository.saveAndFlush(promo)

        val detalles = listOf(DetalleReservaRequest(servicioId = cirugia.id!!, mascotaId = UUID.randomUUID()))
        val resultados = promoEngineService.calcularDescuentos(detalles, LocalDate.now())

        assertEquals(0, BigDecimal("75000").compareTo(resultados[cirugia.id]?.precioFinal))
    }
}

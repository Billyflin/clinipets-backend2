package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.servicios.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryBatchIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var inventarioService: InventarioService

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Autowired
    private lateinit var insumoRepository: InsumoRepository

    @Autowired
    private lateinit var loteInsumoRepository: LoteInsumoRepository

    @Test
    fun `should consume from earliest expiring batch (FEFO)`() {
        // 1. Setup: Insumo with two batches
        val insumo = insumoRepository.saveAndFlush(Insumo(
            nombre = "Vacuna Rabia", stockActual = 20.0, stockMinimo = 5.0, unidadMedida = "Dosis"
        ))

        // Lote 1: Expire in 1 month, 10 units
        val lotePronto = loteInsumoRepository.saveAndFlush(LoteInsumo(
            insumo = insumo, codigoLote = "PRONTO", fechaVencimiento = LocalDate.now().plusMonths(1),
            cantidadInicial = 10.0, cantidadActual = 10.0
        ))

        // Lote 2: Expire in 1 year, 10 units
        val loteLejos = loteInsumoRepository.saveAndFlush(LoteInsumo(
            insumo = insumo, codigoLote = "LEJOS", fechaVencimiento = LocalDate.now().plusYears(1),
            cantidadInicial = 10.0, cantidadActual = 10.0
        ))

        val servicio = servicioMedicoRepository.saveAndFlush(ServicioMedico(
            nombre = "Vacunación Rabia", precioBase = BigDecimal("15000"), duracionMinutos = 15, requierePeso = false
        ))
        servicio.insumos.add(ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 1.0, critico = true))
        servicioMedicoRepository.saveAndFlush(servicio)

        // 2. Consume 12 units
        // Should take 10 from PRONTO and 2 from LEJOS
        inventarioService.consumirStock(servicio.id!!, 12, "Cita-Test-Batch")

        // 3. Verify
        val updatedPronto = loteInsumoRepository.findById(lotePronto.id!!).get()
        val updatedLejos = loteInsumoRepository.findById(loteLejos.id!!).get()

        assertEquals(0.0, updatedPronto.cantidadActual)
        assertEquals(8.0, updatedLejos.cantidadActual)
        
        val updatedInsumo = insumoRepository.findById(insumo.id!!).get()
        assertEquals(8.0, updatedInsumo.stockActual)
    }

    @Test
    fun `should ignore expired batches`() {
        val insumo = insumoRepository.saveAndFlush(Insumo(
            nombre = "Test Expired", stockActual = 10.0, stockMinimo = 2.0, unidadMedida = "Units"
        ))

        // Expired batch
        loteInsumoRepository.saveAndFlush(LoteInsumo(
            insumo = insumo, codigoLote = "OLD", fechaVencimiento = LocalDate.now().minusDays(1),
            cantidadInicial = 10.0, cantidadActual = 10.0
        ))

        val servicio = servicioMedicoRepository.saveAndFlush(ServicioMedico(
            nombre = "Service Expired", precioBase = BigDecimal("1000"), duracionMinutos = 5, requierePeso = false
        ))
        servicio.insumos.add(ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 1.0, critico = true))
        servicioMedicoRepository.saveAndFlush(servicio)

        // Validate availability should return false because only batch is expired
        val available = inventarioService.validarDisponibilidadStock(servicio.id!!, 1)
        assertFalse(available)
    }
}

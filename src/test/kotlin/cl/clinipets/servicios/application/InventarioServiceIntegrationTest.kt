package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.web.ConflictException
import cl.clinipets.servicios.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventarioServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var inventarioService: InventarioService

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Autowired
    private lateinit var insumoRepository: InsumoRepository

    @Autowired
    private lateinit var loteInsumoRepository: LoteInsumoRepository

    @Test
    fun `should validate stock availability for service with direct stock`() {
        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Producto Test",
                precioBase = BigDecimal("5000"),
                duracionMinutos = 0,
                stock = 10,
                requierePeso = false
            )
        )

        assertTrue(inventarioService.validarDisponibilidadStock(servicio.id!!, 5))
        assertTrue(inventarioService.validarDisponibilidadStock(servicio.id!!, 10))
        assertFalse(inventarioService.validarDisponibilidadStock(servicio.id!!, 11))
    }

    @Test
    fun `should consume direct stock from service`() {
        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Producto Consumible",
                precioBase = BigDecimal("5000"),
                duracionMinutos = 0,
                stock = 10,
                requierePeso = false
            )
        )

        inventarioService.consumirStock(servicio.id!!, 3, "TEST-REF-1")

        val updated = servicioMedicoRepository.findById(servicio.id!!).get()
        assertEquals(7, updated.stock)
    }

    @Test
    fun `should throw exception when direct stock is insufficient`() {
        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Producto Insuficiente",
                precioBase = BigDecimal("5000"),
                duracionMinutos = 0,
                stock = 2,
                requierePeso = false
            )
        )

        assertThrows(ConflictException::class.java) {
            inventarioService.consumirStock(servicio.id!!, 5, "TEST-REF-2")
        }
    }

    @Test
    fun `should consume specific insumo for recipes`() {
        val insumo = insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Jarabe Test", stockActual = 100.0, stockMinimo = 10.0, unidadMedida = "ml"
            )
        )
        loteInsumoRepository.saveAndFlush(
            LoteInsumo(
                insumo = insumo, codigoLote = "LOT1", fechaVencimiento = LocalDate.now().plusMonths(6),
                cantidadInicial = 100.0, cantidadActual = 100.0
            )
        )

        inventarioService.consumirStockInsumo(insumo.id!!, 25.5, "RECETA-123")

        val updated = insumoRepository.findById(insumo.id!!).get()
        assertEquals(74.5, updated.stockActual)
    }

    @Test
    fun `should return stock and create emergency batch if no active batches exist`() {
        val insumo = insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Insumo Devolucion", stockActual = 0.0, stockMinimo = 5.0, unidadMedida = "Unit"
            )
        )
        // No batches for this insumo

        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio Devolucion",
                precioBase = BigDecimal("1000"),
                duracionMinutos = 10,
                requierePeso = false
            )
        )
        servicio.insumos.add(
            ServicioInsumo(
                servicio = servicio,
                insumo = insumo,
                cantidadRequerida = 2.0,
                critico = true
            )
        )
        val savedServicio = servicioMedicoRepository.saveAndFlush(servicio)

        inventarioService.devolverStock(savedServicio, 1)

        val updatedInsumo = insumoRepository.findById(insumo.id!!).get()
        assertEquals(2.0, updatedInsumo.stockActual)

        val lotes = loteInsumoRepository.findAll().filter { it.insumo.id == insumo.id }
        assertEquals(1, lotes.size)
        assertTrue(lotes[0].codigoLote.startsWith("DEV-"))
    }
}

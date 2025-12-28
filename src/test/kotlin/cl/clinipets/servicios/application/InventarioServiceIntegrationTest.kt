package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.web.ConflictException
import cl.clinipets.servicios.api.InsumoCreateRequest
import cl.clinipets.servicios.api.InsumoUpdateRequest
import cl.clinipets.servicios.api.LoteCreateRequest
import cl.clinipets.servicios.api.LoteStockAjusteRequest
import cl.clinipets.servicios.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.LocalDate

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

    @BeforeEach
    fun setup() {
        loteInsumoRepository.deleteAll()
        servicioMedicoRepository.deleteAll()
        insumoRepository.deleteAll()
    }

    @Test
    fun `crear insumo exitosamente`() {
        val request = InsumoCreateRequest("Nuevo Insumo", 10.0, "U", "contra")
        val response = inventarioService.crearInsumo(request)

        assertNotNull(response.id)
        assertEquals("Nuevo Insumo", response.nombre)
        assertEquals(10.0, response.stockMinimo)
        assertEquals("contra", response.contraindicacionMarcador)
    }

    @Test
    fun `actualizar insumo exitosamente`() {
        val creado = inventarioService.crearInsumo(InsumoCreateRequest("Orig", 5.0, "U"))
        val update = InsumoUpdateRequest("Mod", 15.0, "ml", "new-contra")

        val actualizado = inventarioService.actualizarInsumo(creado.id, update)

        assertEquals("Mod", actualizado.nombre)
        assertEquals(15.0, actualizado.stockMinimo)
        assertEquals("ml", actualizado.unidadMedida)
        assertEquals("new-contra", actualizado.contraindicacionMarcador)
    }

    @Test
    fun `eliminar insumo exitosamente`() {
        val creado = inventarioService.crearInsumo(InsumoCreateRequest("Delete Me", 1.0, "U"))
        inventarioService.eliminarInsumo(creado.id)

        assertFalse(insumoRepository.existsById(creado.id))
    }

    @Test
    fun `agregar lote a insumo`() {
        val insumo = inventarioService.crearInsumo(InsumoCreateRequest("Insumo Lote", 1.0, "U"))
        val request = LoteCreateRequest("L-001", LocalDate.now().plusYears(1), 50.0)

        val response = inventarioService.agregarLote(insumo.id, request)

        assertEquals(50.0, response.stockActual)
        assertEquals(1, response.lotes.size)
        assertEquals("L-001", response.lotes[0].codigoLote)
    }

    @Test
    fun `ajustar stock de lote`() {
        val insumo = inventarioService.crearInsumo(InsumoCreateRequest("Insumo Ajuste", 1.0, "U"))
        val loteRes =
            inventarioService.agregarLote(insumo.id, LoteCreateRequest("L-1", LocalDate.now().plusDays(10), 10.0))
        val loteId = loteRes.lotes[0].id

        val response = inventarioService.ajustarStockLote(loteId, LoteStockAjusteRequest(25.0, "Ajuste manual"))

        assertEquals(25.0, response.stockActual)
        assertEquals(25.0, response.lotes[0].cantidadActual)
    }

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

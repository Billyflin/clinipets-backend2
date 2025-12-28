package cl.clinipets.servicios.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class InventarioServiceTest {

    private val servicioMedicoRepository: ServicioMedicoRepository = mock()
    private val insumoRepository: InsumoRepository = mock()
    private val citaRepository: CitaRepository = mock()
    private val loteInsumoRepository: LoteInsumoRepository = mock()
    private val servicioInsumoRepository: ServicioInsumoRepository = mock()

    private val inventarioService = InventarioService(
        servicioMedicoRepository,
        insumoRepository,
        citaRepository,
        loteInsumoRepository,
        servicioInsumoRepository
    )

    private fun createServicio(id: UUID = UUID.randomUUID(), stock: Int? = null): ServicioMedico {
        return ServicioMedico(
            id = id,
            nombre = "Servicio Test",
            precioBase = BigDecimal("1000"),
            requierePeso = false,
            duracionMinutos = 30,
            stock = stock
        )
    }

    private fun createInsumo(id: UUID = UUID.randomUUID(), nombre: String = "Insumo Test"): Insumo {
        return Insumo(
            id = id,
            nombre = nombre,
            stockActual = 100.0,
            stockMinimo = 10.0,
            unidadMedida = "Units"
        )
    }

    private fun createLote(
        insumo: Insumo,
        cantidad: Double,
        vencimiento: LocalDate = LocalDate.now().plusDays(1)
    ): LoteInsumo {
        return LoteInsumo(
            id = UUID.randomUUID(),
            insumo = insumo,
            codigoLote = "LOTE-" + UUID.randomUUID().toString().take(4),
            fechaVencimiento = vencimiento,
            cantidadInicial = cantidad,
            cantidadActual = cantidad
        )
    }

    // --- validarDisponibilidadReserva ---

    @Test
    fun `validarDisponibilidadReserva throws NotFoundException when service missing`() {
        val id = UUID.randomUUID()
        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.empty())
        assertThrows<NotFoundException> { inventarioService.validarDisponibilidadReserva(id, 1) }
    }

    @Test
    fun `validarDisponibilidadReserva returns false when service stock is insufficient`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id, stock = 10)
        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(citaRepository.countReservedStock(id)).thenReturn(8L)

        assertFalse(inventarioService.validarDisponibilidadReserva(id, 3)) // 10 - 8 = 2 available, want 3
    }

    @Test
    fun `validarDisponibilidadReserva returns false when critical insumo has insufficient stock`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 5.0, critico = true)
        servicio.insumos.add(si)

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findById(insumo.id!!)).thenReturn(Optional.of(insumo))
        // stockVigente will be 0 because no lots added

        assertFalse(inventarioService.validarDisponibilidadReserva(id, 1))
    }

    @Test
    fun `validarDisponibilidadReserva returns true when everything is ok`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id, stock = 10)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 5.0, critico = true)
        servicio.insumos.add(si)

        val lote = createLote(insumo, 10.0)
        insumo.lotes.add(lote)

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(citaRepository.countReservedStock(id)).thenReturn(0L)
        whenever(insumoRepository.findById(insumo.id!!)).thenReturn(Optional.of(insumo))

        assertTrue(inventarioService.validarDisponibilidadReserva(id, 2))
    }

    // --- validarDisponibilidadStock ---

    @Test
    fun `validarDisponibilidadStock returns false when service stock insufficient`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id, stock = 5)
        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        assertFalse(inventarioService.validarDisponibilidadStock(id, 6))
    }

    @Test
    fun `validarDisponibilidadStock returns false when critical insumo stock insufficient`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 10.0, critico = true)
        servicio.insumos.add(si)

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findById(insumo.id!!)).thenReturn(Optional.of(insumo))

        assertFalse(inventarioService.validarDisponibilidadStock(id, 1))
    }

    @Test
    fun `validarDisponibilidadStock returns false when insumo not found`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 10.0, critico = true)
        servicio.insumos.add(si)

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findById(insumo.id!!)).thenReturn(Optional.empty())

        assertFalse(inventarioService.validarDisponibilidadStock(id, 1))
    }

    @Test
    fun `validarDisponibilidadStock returns true even if non-critical insumo insufficient`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 10.0, critico = false)
        servicio.insumos.add(si)

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findById(insumo.id!!)).thenReturn(Optional.of(insumo))

        assertTrue(inventarioService.validarDisponibilidadStock(id, 1))
    }

    // --- consumirStock ---

    @Test
    fun `consumirStock throws ConflictException when insufficient direct stock`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id, stock = 2)
        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        assertThrows<ConflictException> { inventarioService.consumirStock(id, 3, "REF") }
    }

    @Test
    fun `consumirStock throws NotFoundException when insumo missing`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 1.0)
        servicio.insumos.add(si)

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(null)

        assertThrows<NotFoundException> { inventarioService.consumirStock(id, 1, "REF") }
    }

    @Test
    fun `consumirStock handles multiple lots FEFO and throws ConflictException for critical missing`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 10.0, critico = true)
        servicio.insumos.add(si)

        val lote1 = createLote(insumo, 4.0, LocalDate.now().plusDays(1))
        val lote2 = createLote(insumo, 4.0, LocalDate.now().plusDays(2))

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(insumo)
        whenever(loteInsumoRepository.findVigentesOrderByVencimiento(eq(insumo.id!!), any())).thenReturn(
            listOf(
                lote1,
                lote2
            )
        )

        // Requesting 10.0, only 8.0 available across lots
        assertThrows<ConflictException> { inventarioService.consumirStock(id, 1, "REF") }

        assertEquals(0.0, lote1.cantidadActual)
        assertEquals(0.0, lote2.cantidadActual)
        verify(loteInsumoRepository, times(2)).save(any())
    }

    @Test
    fun `consumirStock works for non-critical even if insufficient`() {
        val id = UUID.randomUUID()
        val servicio = createServicio(id, stock = 10)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 10.0, critico = false)
        servicio.insumos.add(si)

        val lote1 = createLote(insumo, 4.0)

        whenever(servicioMedicoRepository.findById(id)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(insumo)
        whenever(loteInsumoRepository.findVigentesOrderByVencimiento(eq(insumo.id!!), any())).thenReturn(listOf(lote1))

        inventarioService.consumirStock(id, 1, "REF")

        assertEquals(9, servicio.stock)
        assertEquals(0.0, lote1.cantidadActual)
        assertEquals(96.0, insumo.stockActual) // 100 - 4
    }

    // --- consumirStockInsumo ---

    @Test
    fun `consumirStockInsumo throws NotFoundException when insumo missing`() {
        val id = UUID.randomUUID()
        whenever(insumoRepository.findByIdWithLock(id)).thenReturn(null)
        assertThrows<NotFoundException> { inventarioService.consumirStockInsumo(id, 1.0, "REF") }
    }

    @Test
    fun `consumirStockInsumo throws ConflictException when insufficient stock`() {
        val insumo = createInsumo()
        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(insumo)
        whenever(loteInsumoRepository.findVigentesOrderByVencimiento(eq(insumo.id!!), any())).thenReturn(emptyList())

        assertThrows<ConflictException> { inventarioService.consumirStockInsumo(insumo.id!!, 1.0, "REF") }
    }

    @Test
    fun `consumirStockInsumo consumes across lots`() {
        val insumo = createInsumo()
        val lote1 = createLote(insumo, 5.0)
        val lote2 = createLote(insumo, 10.0)

        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(insumo)
        whenever(loteInsumoRepository.findVigentesOrderByVencimiento(eq(insumo.id!!), any())).thenReturn(
            listOf(
                lote1,
                lote2
            )
        )

        inventarioService.consumirStockInsumo(insumo.id!!, 7.0, "REF")

        assertEquals(0.0, lote1.cantidadActual)
        assertEquals(8.0, lote2.cantidadActual)
        assertEquals(93.0, insumo.stockActual)
    }

    // --- devolverStock ---

    @Test
    fun `devolverStock returns early if service not found`() {
        val servicio = createServicio()
        whenever(servicioMedicoRepository.findById(servicio.id!!)).thenReturn(Optional.empty())
        inventarioService.devolverStock(servicio, 1)
        verify(servicioMedicoRepository, never()).save(any())
    }

    @Test
    fun `devolverStock returns to existing lot`() {
        val servicio = createServicio(stock = 10)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 5.0)
        servicio.insumos.add(si)

        val lote = createLote(insumo, 0.0)

        whenever(servicioMedicoRepository.findById(servicio.id!!)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(insumo)
        whenever(loteInsumoRepository.findVigentesOrderByVencimiento(eq(insumo.id!!), any())).thenReturn(listOf(lote))

        inventarioService.devolverStock(servicio, 1)

        assertEquals(11, servicio.stock)
        assertEquals(5.0, lote.cantidadActual)
        assertEquals(105.0, insumo.stockActual)
        verify(loteInsumoRepository).save(lote)
    }

    @Test
    fun `devolverStock handles insumo not found during loop`() {
        val servicio = createServicio(stock = 10)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 5.0)
        servicio.insumos.add(si)

        whenever(servicioMedicoRepository.findById(servicio.id!!)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(null)

        inventarioService.devolverStock(servicio, 1)

        assertEquals(11, servicio.stock)
        verify(insumoRepository, never()).save(any())
    }

    @Test
    fun `devolverStock creates emergency lot when no vigentes lots exist`() {
        val servicio = createServicio(stock = 10)
        val insumo = createInsumo()
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 5.0)
        servicio.insumos.add(si)

        whenever(servicioMedicoRepository.findById(servicio.id!!)).thenReturn(Optional.of(servicio))
        whenever(insumoRepository.findByIdWithLock(insumo.id!!)).thenReturn(insumo)
        whenever(loteInsumoRepository.findVigentesOrderByVencimiento(eq(insumo.id!!), any())).thenReturn(emptyList())

        inventarioService.devolverStock(servicio, 1)

        assertEquals(11, servicio.stock)
        assertEquals(105.0, insumo.stockActual)
        verify(loteInsumoRepository).save(argThat {
            this.codigoLote.startsWith("DEV-") &&
                    this.cantidadActual == 5.0 &&
                    this.insumo == insumo
        })
    }
}

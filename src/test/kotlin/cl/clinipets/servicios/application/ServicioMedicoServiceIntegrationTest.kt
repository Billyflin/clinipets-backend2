package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.api.InsumoRequest
import cl.clinipets.servicios.api.ReglaPrecioRequest
import cl.clinipets.servicios.api.ServicioCreateRequest
import cl.clinipets.servicios.api.ServicioUpdateRequest
import cl.clinipets.servicios.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ServicioMedicoServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var servicioMedicoService: ServicioMedicoService

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Autowired
    private lateinit var insumoRepository: InsumoRepository

    @Autowired
    private lateinit var promocionRepository: PromocionRepository

    @BeforeEach
    fun setup() {
        promocionRepository.deleteAll()
        servicioMedicoRepository.deleteAll()
        insumoRepository.deleteAll()
    }

    @Test
    fun `crear servicio exitosamente`() {
        val request = ServicioCreateRequest(
            nombre = "Nuevo Test",
            precioBase = BigDecimal("10000"),
            requierePeso = false,
            duracionMinutos = 30
        )

        val dto = servicioMedicoService.crear(request)

        assertNotNull(dto.id)
        assertEquals("Nuevo Test", dto.nombre)
        assertTrue(dto.activo)
    }

    @Test
    fun `crear servicio con nombre duplicado lanza excepcion`() {
        val request = ServicioCreateRequest(
            nombre = "Duplicado",
            precioBase = BigDecimal("10000"),
            requierePeso = false,
            duracionMinutos = 30
        )
        servicioMedicoService.crear(request)

        assertThrows(ConflictException::class.java) {
            servicioMedicoService.crear(request)
        }
    }

    @Test
    fun `crear servicio con insumo inexistente lanza excepcion`() {
        val request = ServicioCreateRequest(
            nombre = "Con Insumo Mal",
            precioBase = BigDecimal("10000"),
            requierePeso = false,
            duracionMinutos = 30,
            insumos = listOf(InsumoRequest(UUID.randomUUID(), 1.0, true))
        )

        assertThrows(NotFoundException::class.java) {
            servicioMedicoService.crear(request)
        }
    }

    @Test
    fun `actualizar servicio exitosamente`() {
        val creado = servicioMedicoService.crear(
            ServicioCreateRequest(
                nombre = "Original",
                precioBase = BigDecimal("10000"),
                requierePeso = false,
                duracionMinutos = 30
            )
        )

        val update = ServicioUpdateRequest(
            nombre = "Actualizado",
            precioBase = BigDecimal("15000"),
            requierePeso = true,
            duracionMinutos = 45,
            activo = false,
            categoria = CategoriaServicio.VACUNA,
            especiesPermitidas = null,
            stock = 10,
            bloqueadoSiEsterilizado = true,
            actualizaMarcador = "marcador",
            condicionMarcadorClave = "clave",
            condicionMarcadorValor = "valor"
        )

        val actualizado = servicioMedicoService.actualizar(creado.id, update)

        assertEquals("Actualizado", actualizado.nombre)
        assertEquals(BigDecimal("15000"), actualizado.precioBase)
        assertTrue(actualizado.requierePeso)
        assertEquals(45, actualizado.duracionMinutos)
        assertFalse(actualizado.activo)
        assertEquals(CategoriaServicio.VACUNA, actualizado.categoria)
        assertEquals(10, actualizado.stock)
        assertTrue(actualizado.bloqueadoSiEsterilizado)
        assertEquals("marcador", actualizado.actualizaMarcador)
    }

    @Test
    fun `eliminar servicio realiza soft delete`() {
        val creado = servicioMedicoService.crear(
            ServicioCreateRequest(
                nombre = "Para Borrar",
                precioBase = BigDecimal("10000"),
                requierePeso = false,
                duracionMinutos = 30
            )
        )

        servicioMedicoService.eliminar(creado.id)

        val softDeleted = servicioMedicoRepository.findById(creado.id).get()
        assertFalse(softDeleted.activo)
    }

    @Test
    fun `agregar y eliminar regla de precio`() {
        val creado = servicioMedicoService.crear(
            ServicioCreateRequest(
                nombre = "Con Reglas",
                precioBase = BigDecimal("10000"),
                requierePeso = true,
                duracionMinutos = 30
            )
        )

        val request = ReglaPrecioRequest(0.0, 5.0, BigDecimal("8000"))
        val conRegla = servicioMedicoService.agregarRegla(creado.id, request)

        assertEquals(1, conRegla.reglas.size)
        assertEquals(BigDecimal("8000"), conRegla.reglas[0].precio)

        val reglaId = conRegla.reglas[0].id
        val sinRegla = servicioMedicoService.eliminarRegla(creado.id, reglaId)

        assertTrue(sinRegla.reglas.isEmpty())
    }

    @Test
    fun `should detect cyclic dependency when updating dependencies`() {
        // A -> B
        val servicioA = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio A",
                precioBase = BigDecimal("1000"),
                precioAbono = null,
                duracionMinutos = 30,
                requierePeso = false,
                categoria = CategoriaServicio.CONSULTA
            )
        )
        val servicioB = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio B",
                precioBase = BigDecimal("1000"),
                precioAbono = null,
                duracionMinutos = 30,
                requierePeso = false,
                categoria = CategoriaServicio.CONSULTA
            )
        )

        servicioMedicoService.actualizarDependencias(servicioA.id!!, setOf(servicioB.id!!))

        // Try to make B depend on A (B -> A), which creates A -> B -> A
        assertThrows(ConflictException::class.java) {
            servicioMedicoService.actualizarDependencias(servicioB.id!!, setOf(servicioA.id!!))
        }
    }

    @Test
    fun `actualizar dependencias lanza error si algun id no existe`() {
        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Test Dep",
                precioBase = BigDecimal("1000"),
                precioAbono = null,
                duracionMinutos = 30,
                requierePeso = false,
                categoria = CategoriaServicio.CONSULTA
            )
        )

        assertThrows(NotFoundException::class.java) {
            servicioMedicoService.actualizarDependencias(servicio.id!!, setOf(UUID.randomUUID()))
        }
    }

    @Test
    fun `should filter out services without direct stock in listarActivos`() {
        // Service with stock
        servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Con Stock",
                precioBase = BigDecimal("1000"),
                precioAbono = null,
                duracionMinutos = 0,
                stock = 5,
                activo = true,
                requierePeso = false,
                categoria = CategoriaServicio.PRODUCTO
            )
        )

        // Service without stock
        servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Sin Stock",
                precioBase = BigDecimal("1000"),
                precioAbono = null,
                duracionMinutos = 0,
                stock = 0,
                activo = true,
                requierePeso = false,
                categoria = CategoriaServicio.PRODUCTO
            )
        )

        val activos = servicioMedicoService.listarActivos()

        assertTrue(activos.any { it.nombre == "Con Stock" })
        assertFalse(activos.any { it.nombre == "Sin Stock" })
    }

    @Test
    fun `should filter out services with insufficient critical insumos in listarActivos`() {
        val insumo = insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Insumo Critico", stockActual = 0.5, stockMinimo = 1.0, unidadMedida = "Unit"
            )
        )

        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio con Insumo Insuficiente",
                precioBase = BigDecimal("1000"),
                precioAbono = null,
                duracionMinutos = 15,
                activo = true,
                requierePeso = false,
                categoria = CategoriaServicio.OTRO
            )
        )

        // Requires 1.0 but only has 0.5
        val si = ServicioInsumo(servicio = servicio, insumo = insumo, cantidadRequerida = 1.0, critico = true)
        servicio.insumos.add(si)
        servicioMedicoRepository.saveAndFlush(servicio)

        val activos = servicioMedicoService.listarActivos()
        assertFalse(activos.any { it.nombre == "Servicio con Insumo Insuficiente" })
    }
}

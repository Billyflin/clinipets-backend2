package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.web.ConflictException
import cl.clinipets.servicios.domain.Insumo
import cl.clinipets.servicios.domain.InsumoRepository
import cl.clinipets.servicios.domain.ServicioInsumo
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.junit.jupiter.api.Assertions.*
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

    @Test
    fun `should detect cyclic dependency when updating dependencies`() {
        // A -> B
        val servicioA = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio A", precioBase = BigDecimal("1000"), duracionMinutos = 30, requierePeso = false
            )
        )
        val servicioB = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio B", precioBase = BigDecimal("1000"), duracionMinutos = 30, requierePeso = false
            )
        )

        servicioMedicoService.actualizarDependencias(servicioA.id!!, setOf(servicioB.id!!))

        // Try to make B depend on A (B -> A), which creates A -> B -> A
        assertThrows(ConflictException::class.java) {
            servicioMedicoService.actualizarDependencias(servicioB.id!!, setOf(servicioA.id!!))
        }
    }

    @Test
    fun `should filter out services without direct stock in listarActivos`() {
        // Service with stock
        servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Con Stock",
                precioBase = BigDecimal("1000"),
                duracionMinutos = 0,
                stock = 5,
                activo = true,
                requierePeso = false
            )
        )

        // Service without stock
        servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Sin Stock",
                precioBase = BigDecimal("1000"),
                duracionMinutos = 0,
                stock = 0,
                activo = true,
                requierePeso = false
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
                duracionMinutos = 15,
                activo = true,
                requierePeso = false
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

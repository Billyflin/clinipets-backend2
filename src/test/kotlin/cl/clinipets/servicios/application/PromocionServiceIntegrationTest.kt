package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.api.PromocionBeneficioRequest
import cl.clinipets.servicios.api.PromocionCreateRequest
import cl.clinipets.servicios.api.PromocionUpdateRequest
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.servicios.domain.TipoDescuento
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class PromocionServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var promocionService: PromocionService

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Autowired
    private lateinit var promocionRepository: cl.clinipets.servicios.domain.PromocionRepository

    private lateinit var servicio: ServicioMedico

    @BeforeEach
    fun setup() {
        promocionRepository.deleteAll()
        servicioMedicoRepository.deleteAll()

        servicio = servicioMedicoRepository.save(
            ServicioMedico(
                nombre = "Servicio Base",
                precioBase = BigDecimal("10000"),
                requierePeso = false,
                duracionMinutos = 30,
                activo = true,
                categoria = CategoriaServicio.CONSULTA
            )
        )
    }

    @Test
    fun `crear y listar promociones`() {
        val request = PromocionCreateRequest(
            nombre = "Promo Test",
            descripcion = "Desc",
            fechaInicio = LocalDate.now(),
            fechaFin = LocalDate.now().plusDays(30),
            serviciosTriggerIds = setOf(servicio.id!!),
            beneficios = listOf(
                PromocionBeneficioRequest(servicio.id!!, TipoDescuento.PORCENTAJE_OFF, BigDecimal("10"))
            )
        )

        val created = promocionService.crear(request)
        assertNotNull(created.id)
        assertEquals("Promo Test", created.nombre)
        assertEquals(1, created.serviciosTrigger.size)
        assertEquals(1, created.beneficios.size)

        val all = promocionService.listarTodas()
        assertEquals(1, all.size)
    }

    @Test
    fun `actualizar promocion`() {
        val request = PromocionCreateRequest(
            nombre = "Promo Original",
            fechaInicio = LocalDate.now(),
            fechaFin = LocalDate.now().plusDays(30)
        )
        val created = promocionService.crear(request)

        val update = PromocionUpdateRequest(
            nombre = "Promo Actualizada",
            descripcion = null,
            fechaInicio = null,
            fechaFin = null,
            diasPermitidos = null,
            activa = false
        )
        val updated = promocionService.actualizar(created.id!!, update)

        assertEquals("Promo Actualizada", updated.nombre)
        assertFalse(updated.activa)
    }

    @Test
    fun `eliminar promocion`() {
        val request = PromocionCreateRequest(
            nombre = "Para Borrar",
            fechaInicio = LocalDate.now(),
            fechaFin = LocalDate.now().plusDays(30)
        )
        val created = promocionService.crear(request)

        promocionService.eliminar(created.id!!)

        assertThrows(NotFoundException::class.java) {
            promocionService.actualizar(
                created.id!!, PromocionUpdateRequest(
                    nombre = "Fail",
                    descripcion = null,
                    fechaInicio = null,
                    fechaFin = null,
                    diasPermitidos = null,
                    activa = null
                )
            )
        }
    }
}

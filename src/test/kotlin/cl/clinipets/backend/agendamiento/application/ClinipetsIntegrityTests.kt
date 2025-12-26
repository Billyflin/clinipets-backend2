package cl.clinipets.backend.agendamiento.application

import cl.clinipets.agendamiento.api.FinalizarCitaRequest
import cl.clinipets.agendamiento.api.SignosVitalesRequest
import cl.clinipets.agendamiento.domain.*
import cl.clinipets.servicios.domain.*
import cl.clinipets.veterinaria.domain.*
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.UserRole
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClinipetsIntegrityTests {

    @Autowired
    private lateinit var mockMvc: MockMvc
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    @Autowired
    private lateinit var citaRepository: CitaRepository
    @Autowired
    private lateinit var insumoRepository: InsumoRepository
    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository
    @Autowired
    private lateinit var mascotaRepository: MascotaRepository
    @Autowired
    private lateinit var userRepository: cl.clinipets.identity.domain.UserRepository

    @MockBean
    private lateinit var storageService: cl.clinipets.core.storage.StorageService
    @SpyBean
    private lateinit var signosVitalesRepository: SignosVitalesRepository

    private lateinit var testMascotaId: UUID
    private lateinit var testServicioId: UUID
    private lateinit var testInsumoId: UUID
    private lateinit var testTutorId: UUID

    @BeforeEach
    fun setup() {
        citaRepository.deleteAll()
        servicioMedicoRepository.deleteAll()
        insumoRepository.deleteAll()
        mascotaRepository.deleteAll()
        userRepository.deleteAll()

        val tutor = userRepository.saveAndFlush(
            cl.clinipets.identity.domain.User(
                email = "tutor@test.com",
                name = "Tutor",
                passwordHash = "hash",
                role = UserRole.CLIENT
            )
        )
        testTutorId = tutor.id!!

        val insumo = insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Gasas Esterilizadas",
                stockActual = 10.0,
                stockMinimo = 5,
                unidadMedida = "unidades"
            )
        )
        testInsumoId = insumo.id!!

        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Curación Simple",
                precioBase = 5000,
                requierePeso = false,
                duracionMinutos = 15,
                activo = true
            )
        )
        testServicioId = servicio.id!!

        servicio.insumos.add(
            ServicioInsumo(
                servicio = servicio,
                insumo = insumo,
                cantidadRequerida = 1.0,
                critico = true
            )
        )
        servicioMedicoRepository.saveAndFlush(servicio)

        val mascota = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Buddy",
                especie = Especie.PERRO,
                pesoActual = 10.0,
                raza = "Golden",
                sexo = Sexo.MACHO,
                fechaNacimiento = LocalDate.now().minusYears(2),
                tutor = tutor
            )
        )
        testMascotaId = mascota.id!!
    }

    private fun staffAuth() = authentication(
        UsernamePasswordAuthenticationToken(
            JwtPayload(UUID.randomUUID(), "staff@test.cl", UserRole.STAFF, Instant.now().plusSeconds(3600)),
            "token",
            listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STAFF"))
        )
    )

    private fun userAuth() = authentication(
        UsernamePasswordAuthenticationToken(
            JwtPayload(UUID.randomUUID(), "user@test.cl", UserRole.CLIENT, Instant.now().plusSeconds(3600)),
            "token",
            listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        )
    )

    @Test
    fun `Test de Seguridad por Rol - Alertas de Inventario`() {
        mockMvc.perform(get("/api/v1/inventario/alertas").with(userAuth()))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/inventario/alertas").with(staffAuth()))
            .andExpect(status().isOk)
    }

    @Test
    fun `Test de Integridad de Contrato - Pasaporte de Salud`() {
        mockMvc.perform(get("/api/mascotas/${testMascotaId}/pasaporte-salud").with(staffAuth()))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.mascotaId").value(testMascotaId.toString()))
    }

    @Test
    fun `Test de Atomicidad - Rollback si falla guardado clinico`() {
        val cita = citaRepository.saveAndFlush(
            Cita(
                fechaHoraInicio = Instant.now(),
                fechaHoraFin = Instant.now().plusSeconds(1800),
                estado = EstadoCita.CONFIRMADA,
                precioFinal = 5000,
                tutorId = testTutorId,
                origen = OrigenCita.APP
            )
        )
        cita.detalles.add(
            DetalleCita(
                cita = cita,
                servicio = servicioMedicoRepository.findById(testServicioId).get(),
                mascota = mascotaRepository.findById(testMascotaId).get(),
                precioUnitario = 5000
            )
        )
        citaRepository.saveAndFlush(cita)

        doThrow(RuntimeException("Error simulado")).whenever(signosVitalesRepository).save(any<SignosVitales>())

        val request = FinalizarCitaRequest(
            metodoPago = MetodoPago.EFECTIVO,
            signosVitales = mapOf(testMascotaId to SignosVitalesRequest(10.0, 38.5, 80))
        )

        mockMvc.perform(
            post("/api/v1/reservas/${cita.id}/finalizar")
                .with(staffAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)

        val insumo = insumoRepository.findById(testInsumoId).get()
        assertEquals(10.0, insumo.stockActual)
    }

    @Test
    fun `Test de Concurrencia - Stock nunca negativo`() {
        val insumo = insumoRepository.findById(testInsumoId).get()
        insumo.stockActual = 2.0
        insumoRepository.saveAndFlush(insumo)

        val citasIds = (1..5).map {
            val cita = citaRepository.saveAndFlush(
                Cita(
                    fechaHoraInicio = Instant.now(),
                    fechaHoraFin = Instant.now().plusSeconds(1800),
                    estado = EstadoCita.CONFIRMADA,
                    precioFinal = 5000,
                    tutorId = testTutorId,
                    origen = OrigenCita.APP
                )
            )
            cita.detalles.add(
                DetalleCita(
                    cita = cita,
                    servicio = servicioMedicoRepository.findById(testServicioId).get(),
                    mascota = mascotaRepository.findById(testMascotaId).get(),
                    precioUnitario = 5000
                )
            )
            citaRepository.saveAndFlush(cita).id!!
        }

        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val futures = citasIds.map { id ->
            CompletableFuture.runAsync {
                try {
                    val result = mockMvc.perform(
                        post("/api/v1/reservas/$id/finalizar")
                            .with(staffAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(FinalizarCitaRequest(metodoPago = MetodoPago.EFECTIVO)))
                    )
                        .andReturn()

                    if (result.response.status == 200) successCount.incrementAndGet()
                    else errorCount.incrementAndGet()
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).join()

        val stockFinal = insumoRepository.findById(testInsumoId).get().stockActual
        assertEquals(2, successCount.get())
        assertEquals(3, errorCount.get())
        assertEquals(0.0, stockFinal)
    }
}

package cl.clinipets.backend.agendamiento.application

import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.pagos.application.PagoService
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=Y29udGFzZWNvbXNlY3JldG9iZGVDbGluSXBldHMxMjM0NTY3OA==",
        "jwt.refresh-secret=cmVmcmVzaFNlY3JldG9iZGVDbGluSXBldHMxMjM0NTY3OA==",
        "jwt.issuer=TestIssuer",
        "google.client-id=test-google",
        "mercadopago.access-token=dummy"
    ]
)
class ReservaServiceTest(
    @Autowired private val reservaService: ReservaService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val mascotaRepository: MascotaRepository,
    @Autowired private val servicioMedicoRepository: ServicioMedicoRepository
) {

    @Autowired
    private lateinit var pagoMock: PagoService

    private lateinit var tutorPayload: JwtPayload
    private lateinit var servicio: ServicioMedico
    private lateinit var mascota: Mascota

    @BeforeEach
    fun setup() {
        servicioMedicoRepository.deleteAll()
        mascotaRepository.deleteAll()
        userRepository.deleteAll()

        val tutor = userRepository.save(
            User(
                email = "tutor@test.com",
                name = "Tutor",
                passwordHash = "pw",
                role = UserRole.CLIENT
            )
        )
        tutorPayload = JwtPayload(
            userId = tutor.id!!,
            email = tutor.email,
            role = tutor.role,
            expiresAt = Instant.now()
        )
        servicio = servicioMedicoRepository.save(
            ServicioMedico(
                nombre = "Esterilización Canina",
                precioBase = 30000,
                requierePeso = true,
                duracionMinutos = 60,
                activo = true,
                reglas = mutableListOf()
            )
        )
        val regla = ReglaPrecio(
            pesoMin = BigDecimal("0.0"),
            pesoMax = BigDecimal("10.0"),
            precio = 30000,
            servicio = servicio
        )
        servicio.reglas.add(regla)
        servicio = servicioMedicoRepository.save(servicio)

        mascota = mascotaRepository.save(
            Mascota(
                nombre = "Firulais",
                especie = Especie.PERRO,
                pesoActual = BigDecimal("8.5"),
                // Usamos LocalDate para fecha de nacimiento
                fechaNacimiento = LocalDate.of(2022, 5, 10),
                tutor = tutor
            )
        )
    }

    @Test
    fun `crear reserva genera payment url y cita pendiente`() {
        // Buscamos siguiente sabado
        val fechaSabado = siguienteSabadoAMas(LocalDate.now())
        // Convertimos a Instant a las 11:00 (HorarioClinica abre Sabado a las 10:00, cierra 19:00. 11:00 es válido)
        val inicio = fechaSabado.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant()
        
        val result = reservaService.crearReserva(
            servicioId = servicio.id!!,
            mascotaId = mascota.id!!,
            fechaHoraInicio = inicio,
            origen = OrigenCita.APP,
            tutor = tutorPayload
        )
        assertEquals("https://pago.test", result.paymentUrl)
        assertEquals(servicio.id, result.cita.servicioId)
        assertEquals(mascota.id, result.cita.mascotaId)
        assertEquals(tutorPayload.userId, result.cita.tutorId)
        assertEquals(30000, result.cita.precioFinal)
    }

    private fun siguienteSabadoAMas(hoy: LocalDate): LocalDate {
        var fecha = hoy
        while (fecha.dayOfWeek.value != 6) { // 6 is Saturday
            fecha = fecha.plusDays(1)
        }
        return fecha
    }

    @TestConfiguration
    class PagoMockConfig {
        @Bean
        @Primary
        fun pagoMock(): PagoService = mock {
            on { crearPreferencia(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()) } doReturn "https://pago.test"
        }
    }
}

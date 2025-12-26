package cl.clinipets.agendamiento.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.agendamiento.domain.*
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.notifications.NotificationService
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*

class ReservaServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reservaService: ReservaService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    @Autowired
    private lateinit var citaRepository: CitaRepository

    @Autowired
    private lateinit var servicioMedicoRepository: cl.clinipets.servicios.domain.ServicioMedicoRepository

    @MockitoSpyBean
    private lateinit var notificationService: NotificationService

    @Test
    fun `should finalize appointment and record payment`() {
        val randomId = UUID.randomUUID().toString().take(8)
        // 1. Setup Data
        val tutor = userRepository.saveAndFlush(User(
            name = "Test Tutor",
            email = "tutor-$randomId@test.com",
            phone = "123456",
            passwordHash = "hash",
            role = UserRole.CLIENT
        ))

        val staff = userRepository.saveAndFlush(User(
            name = "Test Staff",
            email = "staff-$randomId@test.com",
            phone = "654321",
            passwordHash = "hash",
            role = UserRole.STAFF
        ))

        val mascota = mascotaRepository.saveAndFlush(Mascota(
            nombre = "Firulais",
            especie = Especie.PERRO,
            sexo = Sexo.MACHO,
            pesoActual = 10.0,
            fechaNacimiento = LocalDate.now().minusYears(1),
            tutor = tutor
        ))

        val servicio = servicioMedicoRepository.saveAndFlush(
            cl.clinipets.servicios.domain.ServicioMedico(
                nombre = "Consulta General",
                precioBase = BigDecimal("15000"),
                duracionMinutos = 30,
                activo = true,
                requierePeso = false
            )
        )

        val cita = Cita(
            fechaHoraInicio = Instant.now(),
            fechaHoraFin = Instant.now().plusSeconds(3600),
            estado = EstadoCita.CONFIRMADA,
            precioFinal = BigDecimal("15000"),
            tutor = tutor,
            origen = OrigenCita.APP
        )
        cita.detalles.add(
            DetalleCita(
                cita = cita,
                servicio = servicio,
                mascota = mascota,
                precioUnitario = BigDecimal("15000")
            )
        )
        cita.cambiarEstado(EstadoCita.EN_ATENCION, "test")
        val savedCita = citaRepository.saveAndFlush(cita)

        val staffPayload = JwtPayload(
            userId = staff.id!!,
            email = staff.email,
            role = staff.role,
            expiresAt = Instant.now().plusSeconds(3600)
        )

        // 2. Execute Action
        val finalized = reservaService.finalizarCita(
            id = savedCita.id!!,
            request = cl.clinipets.agendamiento.api.FinalizarCitaRequest(metodoPago = MetodoPago.EFECTIVO),
            staff = staffPayload
        )

        // 3. Verify
        assertNotNull(finalized)
        assertEquals(EstadoCita.FINALIZADA, finalized.estado)
        assertEquals(1, finalized.pagos.size)
        assertEquals(BigDecimal("15000.00"), finalized.totalPagado().setScale(2))
        assertEquals(BigDecimal.ZERO.setScale(2), finalized.saldoPendiente().setScale(2))
        assertEquals(staff.id, finalized.staffFinalizador?.id)

        // 4. Verify Async Notification
        await untilAsserted {
            verify(notificationService, atLeastOnce()).enviarNotificacion(
                userId = any(),
                titulo = any(),
                cuerpo = any(),
                data = any()
            )
        }
    }
}

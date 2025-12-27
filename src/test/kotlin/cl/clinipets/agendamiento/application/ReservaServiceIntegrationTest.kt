package cl.clinipets.agendamiento.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.agendamiento.api.ReservaItemRequest
import cl.clinipets.agendamiento.domain.*
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.*
import cl.clinipets.servicios.domain.*
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class ReservaServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reservaService: ReservaService

    @Autowired
    private lateinit var gestionAgendaService: GestionAgendaService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Autowired
    private lateinit var citaRepository: CitaRepository

    @Test
    fun `should create reservation successfully`() {
        val tutor = userRepository.saveAndFlush(
            User(
                name = "Tutor 1", email = "tutor_${UUID.randomUUID()}@test.com",
                passwordHash = "hash", role = UserRole.CLIENT
            )
        )

        val mascota = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Bobby", especie = Especie.PERRO, sexo = cl.clinipets.veterinaria.domain.Sexo.MACHO,
                fechaNacimiento = LocalDate.now().minusYears(1), tutor = tutor
            )
        )

        val servicio = ServicioMedico(
            nombre = "Servicio_${UUID.randomUUID()}", precioBase = BigDecimal("15000"),
            duracionMinutos = 30, requierePeso = false, activo = true
        )
        servicio.especiesPermitidas.add(Especie.PERRO)
        val savedServicio = servicioMedicoRepository.saveAndFlush(servicio)

        val jwt = JwtPayload(tutor.id!!, tutor.email, tutor.role, Instant.now().plusSeconds(3600))
        val futureDate = LocalDate.now().plusDays(30)
        val startTime = futureDate.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        val request = listOf(ReservaItemRequest(mascota.id!!, savedServicio.id!!, 1))

        // Llama al servicio
        val result = reservaService.crearReserva(request, startTime, OrigenCita.WEB, jwt)

        assertNotNull(result.cita.id)
        assertEquals(EstadoCita.CONFIRMADA, result.cita.estado)
    }

    @Test
    fun `should cancel reservation successfully`() {
        val tutor = userRepository.saveAndFlush(
            User(
                name = "Tutor Cancel", email = "tutor_cancel_${UUID.randomUUID()}@test.com",
                passwordHash = "hash", role = UserRole.CLIENT
            )
        )

        val mascota = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Bobby", especie = Especie.PERRO, sexo = cl.clinipets.veterinaria.domain.Sexo.MACHO,
                fechaNacimiento = LocalDate.now().minusYears(1), tutor = tutor
            )
        )

        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio Cancel", precioBase = BigDecimal("15000"),
                duracionMinutos = 30, requierePeso = false, activo = true
            )
        )

        val startTime = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS)
        val cita = citaRepository.saveAndFlush(
            Cita(
                fechaHoraInicio = startTime, fechaHoraFin = startTime.plusSeconds(1800),
                estado = EstadoCita.CONFIRMADA, precioFinal = BigDecimal("15000"),
                tutor = tutor, origen = OrigenCita.WEB
            )
        )

        val jwt = JwtPayload(tutor.id!!, tutor.email, tutor.role, Instant.now().plusSeconds(3600))

        val cancelledCita = reservaService.cancelar(cita.id!!, jwt)

        assertEquals(EstadoCita.CANCELADA, cancelledCita.estado)
        val dbCita = citaRepository.findById(cita.id!!).get()
        assertEquals(EstadoCita.CANCELADA, dbCita.estado)
    }

    @Test
    fun `should finalize reservation successfully as staff`() {
        val tutor = userRepository.saveAndFlush(User(
            name = "Tutor Finalize", email = "tutor_fin_${UUID.randomUUID()}@test.com",
            passwordHash = "hash", role = UserRole.CLIENT
        ))

        val staff = userRepository.saveAndFlush(User(
            name = "Staff 1", email = "staff_${UUID.randomUUID()}@test.com",
            passwordHash = "hash", role = UserRole.STAFF
        ))

        val mascota = mascotaRepository.saveAndFlush(Mascota(
            nombre = "Bobby", especie = Especie.PERRO, sexo = cl.clinipets.veterinaria.domain.Sexo.MACHO,
            fechaNacimiento = LocalDate.now().minusYears(1), tutor = tutor
        ))

        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Servicio Fin", precioBase = BigDecimal("15000"),
                duracionMinutos = 30, requierePeso = false, activo = true
            )
        )

        val startTime = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)
        val cita = citaRepository.saveAndFlush(
            Cita(
                fechaHoraInicio = startTime, fechaHoraFin = startTime.plusSeconds(1800),
                estado = EstadoCita.CONFIRMADA, precioFinal = BigDecimal("15000"),
                tutor = tutor, origen = OrigenCita.WEB
            )
        )

        val jwtStaff = JwtPayload(staff.id!!, staff.email, staff.role, Instant.now().plusSeconds(3600))

        // Transition to EN_ATENCION first
        gestionAgendaService.iniciarAtencion(cita.id!!, jwtStaff)

        val finalizedCita = reservaService.finalizarCita(cita.id!!, null, jwtStaff)

        assertEquals(EstadoCita.FINALIZADA, finalizedCita.estado)
        assertEquals(staff.id, finalizedCita.staffFinalizador?.id)
    }
}
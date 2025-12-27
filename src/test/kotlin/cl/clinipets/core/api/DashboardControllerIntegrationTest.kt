package cl.clinipets.core.api

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.agendamiento.domain.*
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

@AutoConfigureMockMvc
@Transactional
class DashboardControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    @Autowired
    private lateinit var citaRepository: CitaRepository

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Test
    fun `should return correct statistics when authorized`() {
        // Setup data
        val staff = userRepository.saveAndFlush(
            User(
                name = "Staff", email = "staff_dashboard@test.com", passwordHash = "hash", role = UserRole.STAFF
            )
        )

        val client = userRepository.saveAndFlush(
            User(
                name = "Client", email = "client_dashboard@test.com", passwordHash = "hash", role = UserRole.CLIENT
            )
        )

        val mascota = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Dashboard Pet", especie = Especie.PERRO, sexo = Sexo.HEMBRA,
                fechaNacimiento = LocalDate.now(), tutor = client
            )
        )

        val servicio = servicioMedicoRepository.saveAndFlush(
            ServicioMedico(
                nombre = "Consulta Dashboard", precioBase = BigDecimal("20000"),
                duracionMinutos = 30, requierePeso = false, activo = true
            )
        )

        val ahora = Instant.now()
        val cita = Cita(
            fechaHoraInicio = ahora, fechaHoraFin = ahora.plusSeconds(1800),
            estado = EstadoCita.FINALIZADA, precioFinal = BigDecimal("20000"),
            tutor = client, origen = OrigenCita.WEB
        )
        cita.detalles.add(
            DetalleCita(
                cita = cita,
                servicio = servicio,
                mascota = mascota,
                precioUnitario = BigDecimal("20000")
            )
        )
        citaRepository.saveAndFlush(cita)

        // Mock security context
        val principal = JwtPayload(staff.id!!, staff.email, staff.role, Instant.now().plusSeconds(3600))
        val auth = UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_STAFF")))

        mockMvc.get("/api/v1/dashboard/estadisticas") {
            with(authentication(auth))
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.totalMascotas") { value(1) }
            jsonPath("$.citasHoy") { value(1) }
            jsonPath("$.ingresosMes") { value(20000.0) }
            jsonPath("$.topServicios[0].nombre") { value("Consulta Dashboard") }
        }
    }

    @Test
    fun `should return forbidden when user is client`() {
        val client = userRepository.saveAndFlush(
            User(
                name = "Client", email = "client_only@test.com", passwordHash = "hash", role = UserRole.CLIENT
            )
        )

        val principal = JwtPayload(client.id!!, client.email, client.role, Instant.now().plusSeconds(3600))
        val auth = UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_CLIENT")))

        mockMvc.get("/api/v1/dashboard/estadisticas") {
            with(authentication(auth))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
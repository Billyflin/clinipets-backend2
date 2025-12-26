package cl.clinipets.backend.agendamiento.application

import cl.clinipets.agendamiento.api.ReservaItemRequest
import cl.clinipets.agendamiento.api.FinalizarCitaRequest
import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.servicios.application.InventarioService
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.storage.StorageService
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=Y29udGFzZWNvbXNlY3JldG9iZGVDbGluSXBldHMxMjM0NTY3OA==",
        "jwt.refresh-secret=cmVmcmVzaFNlY3JldG9iZGVDbGluSXBldHMxMjM0NTY3OA==",
        "jwt.issuer=TestIssuer",
        "google.client-id=test-google"
    ]
)
class ReservaServiceTest(
    @Autowired private val reservaService: ReservaService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val mascotaRepository: MascotaRepository,
    @Autowired private val servicioMedicoRepository: ServicioMedicoRepository,
    @Autowired private val citaRepository: CitaRepository,
    @Autowired private val inventarioService: InventarioService
) {
    @MockBean
    private lateinit var storageService: StorageService

    private lateinit var tutorPayload: JwtPayload
    private lateinit var staffPayload: JwtPayload
    private lateinit var servicio: ServicioMedico
    private lateinit var mascota: Mascota

    @BeforeEach
    fun setup() {
        citaRepository.deleteAll()
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

        val staff = userRepository.save(
            User(
                email = "staff@test.com",
                name = "Staff",
                passwordHash = "pw",
                role = UserRole.STAFF
            )
        )
        staffPayload = JwtPayload(
            userId = staff.id!!,
            email = staff.email,
            role = staff.role,
            expiresAt = Instant.now()
        )

        servicio = servicioMedicoRepository.save(
            ServicioMedico(
                nombre = "Esterilización Canina",
                precioBase = 30000,
                requierePeso = true,
                duracionMinutos = 60,
                activo = true,
                reglas = mutableSetOf(),
                stock = 10 // Default stock
            )
        )
        val regla = ReglaPrecio(
            pesoMin = 0.0,
            pesoMax = 10.0,
            precio = 30000,
            servicio = servicio
        )
        servicio.reglas.add(regla)
        servicio = servicioMedicoRepository.save(servicio)

        mascota = mascotaRepository.save(
            Mascota(
                nombre = "Firulais",
                especie = Especie.PERRO,
                raza = "Mestizo",
                sexo = Sexo.MACHO,
                pesoActual = 8.5,
                // Usamos LocalDate para fecha de nacimiento
                fechaNacimiento = LocalDate.of(2022, 5, 10),
                tutor = tutor
            )
        )
    }

    @Test
    fun `crear reserva genera cita confirmada`() {
        // Buscamos siguiente sabado
        val fechaSabado = siguienteSabadoAMas(LocalDate.now())
        // Convertimos a Instant a las 11:00 (HorarioClinica abre Sabado a las 10:00, cierra 19:00. 11:00 es válido)
        val inicio = fechaSabado.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant()
        
        val result = reservaService.crearReserva(
            detalles = listOf(
                ReservaItemRequest(
                    mascotaId = mascota.id!!,
                    servicioId = servicio.id!!
                )
            ),
            fechaHoraInicio = inicio,
            origen = OrigenCita.APP,
            tutor = tutorPayload
        )
        
        // Cita properties checks
        assertEquals(tutorPayload.userId, result.cita.tutorId)
        assertEquals(30000, result.cita.precioFinal)
        assertEquals(EstadoCita.CONFIRMADA, result.cita.estado)
        
        // Detalles checks
        assertEquals(1, result.cita.detalles.size)
        assertEquals(servicio.id, result.cita.detalles[0].servicio.id)
        assertEquals(mascota.id, result.cita.detalles[0].mascota?.id)
    }

    @Test
    fun `crear reserva falla si no hay stock suficiente`() {
        // Configurar servicio sin stock
        servicio.stock = 0
        servicioMedicoRepository.save(servicio)

        val inicio = siguienteSabadoAMas(LocalDate.now())
            .atTime(11, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()

        assertThrows(BadRequestException::class.java) {
            reservaService.crearReserva(
                detalles = listOf(
                    ReservaItemRequest(
                        mascotaId = mascota.id!!,
                        servicioId = servicio.id!!
                    )
                ),
                fechaHoraInicio = inicio,
                origen = OrigenCita.APP,
                tutor = tutorPayload
            )
        }
    }

    @Test
    fun `no permite reservar slot parcialmente ocupado`() {
        val fechaSabado = siguienteSabadoAMas(LocalDate.now())
        // 11:00
        val inicio1 = fechaSabado.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant()
        
        // Crear cita 1 (60 min)
        reservaService.crearReserva(
            detalles = listOf(ReservaItemRequest(mascota.id!!, servicio.id!!)), // servicio dura 60 min
            fechaHoraInicio = inicio1,
            origen = OrigenCita.APP,
            tutor = tutorPayload
        )
        
        // Intentar crear cita 2 a las 11:30 (30 min dentro de la cita 1)
        val inicio2 = fechaSabado.atTime(11, 30).atZone(ZoneId.systemDefault()).toInstant()
        
        assertThrows(BadRequestException::class.java) {
            reservaService.crearReserva(
                detalles = listOf(ReservaItemRequest(mascota.id!!, servicio.id!!)),
                fechaHoraInicio = inicio2,
                origen = OrigenCita.APP,
                tutor = tutorPayload
            )
        }
    }

    @Test
    fun `devuelve stock si falla finalizacion`() {
        // Setup: Servicio con 2 unidades de stock
        servicio.stock = 2
        servicioMedicoRepository.save(servicio)
        
        val fechaSabado = siguienteSabadoAMas(LocalDate.now())
        val inicio1 = fechaSabado.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant()
        val inicio2 = fechaSabado.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()

        // Crear 2 citas
        val cita1Result = reservaService.crearReserva(
            detalles = listOf(ReservaItemRequest(mascota.id!!, servicio.id!!)),
            fechaHoraInicio = inicio1,
            origen = OrigenCita.APP,
            tutor = tutorPayload
        )
        val cita2Result = reservaService.crearReserva(
            detalles = listOf(ReservaItemRequest(mascota.id!!, servicio.id!!)),
            fechaHoraInicio = inicio2,
            origen = OrigenCita.APP,
            tutor = tutorPayload
        )
        
        val cita1 = cita1Result.cita
        val cita2 = cita2Result.cita
        
        // Cambiar estados a EN_ATENCION para poder finalizar (si se requiere)
        cita1.cambiarEstado(EstadoCita.EN_ATENCION, "test")
        citaRepository.save(cita1)
        cita2.cambiarEstado(EstadoCita.EN_ATENCION, "test")
        citaRepository.save(cita2)

        // Finalizar cita 1 → Stock debería bajar a 1
        reservaService.finalizarCita(cita1.id!!, FinalizarCitaRequest(cl.clinipets.agendamiento.domain.MetodoPago.EFECTIVO, null), staffPayload)
        
        assertEquals(1, servicioMedicoRepository.findById(servicio.id!!).get().stock)
        
        // Consumir manualmente el último stock (simular concurrencia o venta externa)
        inventarioService.consumirStock(servicio.id!!, 1, "test-concurrente")
        assertEquals(0, servicioMedicoRepository.findById(servicio.id!!).get().stock)
        
        // Intentar finalizar cita 2 → Debe fallar por falta de stock
        assertThrows(BadRequestException::class.java) {
            reservaService.finalizarCita(cita2.id!!, FinalizarCitaRequest(cl.clinipets.agendamiento.domain.MetodoPago.EFECTIVO, null), staffPayload)
        }
        
        // Verificar que cita2 quedó CANCELADA
        val cita2After = citaRepository.findById(cita2.id!!).get()
        assertEquals(EstadoCita.CANCELADA, cita2After.estado)
    }

    private fun siguienteSabadoAMas(hoy: LocalDate): LocalDate {
        var fecha = hoy
        while (fecha.dayOfWeek.value != 6) { // 6 is Saturday
            fecha = fecha.plusDays(1)
        }
        return fecha
    }
}

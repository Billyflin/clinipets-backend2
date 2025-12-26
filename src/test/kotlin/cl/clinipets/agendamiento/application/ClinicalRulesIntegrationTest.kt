package cl.clinipets.agendamiento.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.agendamiento.api.ReservaItemRequest
import cl.clinipets.agendamiento.domain.*
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.*
import cl.clinipets.veterinaria.historial.api.FichaCreateRequest
import cl.clinipets.veterinaria.historial.application.FichaClinicaService
import cl.clinipets.veterinaria.historial.application.HistorialClinicoService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClinicalRulesIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reservaService: ReservaService

    @Autowired
    private lateinit var fichaService: FichaClinicaService

    @Autowired
    private lateinit var historialService: HistorialClinicoService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    @Autowired
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

    @Autowired
    private lateinit var citaRepository: CitaRepository

    @Autowired
    private lateinit var hitoMedicoRepository: HitoMedicoRepository

    @Test
    fun `should cancel contraindicated service when health marker updates to positive`() {
        // 1. Setup Data
        val tutor = userRepository.saveAndFlush(User(
            name = "Cat Owner", email = "cat@test.com", passwordHash = "hash", role = UserRole.CLIENT
        ))
        val vet = userRepository.saveAndFlush(User(
            name = "Dr. House", email = "vet@test.com", passwordHash = "hash", role = UserRole.STAFF
        ))
        
        val cat = mascotaRepository.saveAndFlush(Mascota(
            nombre = "Garfield", especie = Especie.GATO, sexo = Sexo.MACHO, tutor = tutor,
            pesoActual = 4.0, fechaNacimiento = java.time.LocalDate.now().minusYears(2)
        ))

        val testLeucemia = servicioMedicoRepository.saveAndFlush(ServicioMedico(
            nombre = "Test Leucemia Felina", precioBase = BigDecimal("15000"), duracionMinutos = 15,
            actualizaMarcador = "LEUCEMIA_FELINA", requierePeso = false
        ))

        val vacunaLeucemia = servicioMedicoRepository.saveAndFlush(ServicioMedico(
            nombre = "Vacuna Leucemia Felina", precioBase = BigDecimal("20000"), duracionMinutos = 15,
            condicionMarcadorClave = "LEUCEMIA_FELINA", condicionMarcadorValor = "NEGATIVO",
            requierePeso = false
        ))

        val tutorPayload = JwtPayload(tutor.id!!, tutor.email, tutor.role, Instant.now().plusSeconds(3600))

        // 2. Book both services (for tomorrow to avoid same-day buffer conflicts)
        val tomorrow = java.time.LocalDate.now().plusDays(1)
        val inicio = tomorrow.atTime(11, 0).atZone(java.time.ZoneId.of("America/Santiago")).toInstant()

        val reserva = reservaService.crearReserva(
            detalles = listOf(
                ReservaItemRequest(cat.id!!, testLeucemia.id!!),
                ReservaItemRequest(cat.id!!, vacunaLeucemia.id!!)
            ),
            fechaHoraInicio = inicio,
            origen = OrigenCita.APP,
            tutor = tutorPayload
        )

        val citaId = reserva.cita.id!!
        assertEquals(BigDecimal("35000.00"), reserva.cita.precioFinal.setScale(2))

        // 3. Create Ficha with POSITIVE result
        fichaService.crearFicha(
            request = FichaCreateRequest(
                mascotaId = cat.id!!,
                citaId = citaId,
                motivoConsulta = "Control y Vacuna",
                marcadores = mapOf("LEUCEMIA_FELINA" to "POSITIVO")
            ),
            autorId = vet.id!!
        )

        // 4. Verify Exclusion
        val updatedCita = citaRepository.findById(citaId).get()
        
        // Final price should be 35000 - 20000 = 15000
        assertEquals(BigDecimal("15000.00"), updatedCita.precioFinal.setScale(2))
        
        val detalleVacuna = updatedCita.detalles.find { it.servicio.id == vacunaLeucemia.id }
        assertNotNull(detalleVacuna)
        assertEquals(EstadoDetalleCita.CANCELADO_CLINICO, detalleVacuna?.estado)
        
        // Verify Marker persists in pet
        val updatedCat = mascotaRepository.findById(cat.id!!).get()
        assertEquals("POSITIVO", updatedCat.marcadores["LEUCEMIA_FELINA"])

        // 5. Verify Milestone (Pillar 1)
        val hitos = hitoMedicoRepository.findAllByMascotaIdOrderByFechaDesc(cat.id!!)
        assertEquals(1, hitos.size)
        assertEquals("LEUCEMIA_FELINA", hitos[0].marcador)
        assertEquals("POSITIVO", hitos[0].valorNuevo)
        assertNull(hitos[0].valorAnterior)
        assertEquals(citaId, hitos[0].citaId)
    }
}

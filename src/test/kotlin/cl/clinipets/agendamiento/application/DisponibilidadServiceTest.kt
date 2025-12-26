package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.*
import cl.clinipets.core.config.ClinicProperties
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import java.math.BigDecimal
import java.time.*
import java.util.*

class DisponibilidadServiceTest {

    private lateinit var citaRepository: CitaRepository
    private lateinit var bloqueoAgendaRepository: BloqueoAgendaRepository
    private lateinit var clinicProperties: ClinicProperties
    private lateinit var availabilityService: DisponibilidadService
    private val zoneId = ZoneId.of("America/Santiago")

    @BeforeEach
    fun setup() {
        citaRepository = mock(CitaRepository::class.java)
        bloqueoAgendaRepository = mock(BloqueoAgendaRepository::class.java)
        clinicProperties = ClinicProperties(
            schedule = mutableMapOf(
                "MONDAY" to "09:00-11:00" // Rango corto para test fácil
            )
        )
        availabilityService = DisponibilidadService(
            citaRepository,
            bloqueoAgendaRepository,
            clinicProperties,
            zoneId
        )
    }

    @Test
    fun `should return empty list if clinic is closed`() {
        val sunday = LocalDate.of(2025, 12, 28) // Domingo
        val slots = availabilityService.obtenerSlots(sunday, 30)
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `should return slots when free`() {
        val monday = LocalDate.of(2025, 12, 29)
        `when`(citaRepository.findOverlappingCitas(any(), any())).thenReturn(emptyList())
        `when`(bloqueoAgendaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(any(), any())).thenReturn(emptyList())

        val slots = availabilityService.obtenerSlots(monday, 30)

        // 09:00 to 11:00 (2 hours)
        // Slots starting at 09:00, 09:15, 09:30, 09:45, 10:00, 10:15, 10:30
        // Because 10:30 + 30m = 11:00 (limit)
        assertEquals(7, slots.size)
        assertEquals(monday.atTime(9, 0).atZone(zoneId).toInstant(), slots[0])
        assertEquals(monday.atTime(10, 30).atZone(zoneId).toInstant(), slots.last())
    }

    @Test
    fun `should filter occupied slots by appointments`() {
        val monday = LocalDate.of(2025, 12, 29)
        val citaInicio = monday.atTime(9, 30).atZone(zoneId).toInstant()
        val citaFin = monday.atTime(10, 0).atZone(zoneId).toInstant()

        val dummyUser = User(name = "Tutor", email = "test@test.com", passwordHash = "", role = UserRole.CLIENT)
        val cita = Cita(
            fechaHoraInicio = citaInicio,
            fechaHoraFin = citaFin,
            estado = EstadoCita.CONFIRMADA,
            precioFinal = BigDecimal.ZERO,
            tutor = dummyUser,
            origen = OrigenCita.APP
        )

        `when`(citaRepository.findOverlappingCitas(any(), any())).thenReturn(listOf(cita))
        `when`(bloqueoAgendaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(any(), any())).thenReturn(emptyList())

        // Request 30 min slot
        val slots = availabilityService.obtenerSlots(monday, 30)

        // Slots that DON'T overlap with 09:30-10:00:
        // 09:00 - 09:30 (OK)
        // 10:00 - 10:30 (OK)
        // 10:15 - 10:45 (OK)
        // 10:30 - 11:00 (OK)
        
        // Slots that DO overlap:
        // 09:15 - 09:45 (CHOCA con 09:30-10:00)
        // 09:30 - 10:00 (CHOCA)
        // 09:45 - 10:15 (CHOCA)

        assertFalse(slots.contains(monday.atTime(9, 15).atZone(zoneId).toInstant()))
        assertFalse(slots.contains(monday.atTime(9, 30).atZone(zoneId).toInstant()))
        assertFalse(slots.contains(monday.atTime(9, 45).atZone(zoneId).toInstant()))
        
        assertTrue(slots.contains(monday.atTime(9, 0).atZone(zoneId).toInstant()))
        assertTrue(slots.contains(monday.atTime(10, 0).atZone(zoneId).toInstant()))
    }
}

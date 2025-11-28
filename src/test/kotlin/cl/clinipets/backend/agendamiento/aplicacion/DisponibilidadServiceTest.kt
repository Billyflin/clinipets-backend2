package cl.clinipets.backend.agendamiento.aplicacion

import cl.clinipets.backend.agendamiento.dominio.Cita
import cl.clinipets.backend.agendamiento.dominio.EstadoCita
import cl.clinipets.backend.agendamiento.dominio.OrigenCita
import cl.clinipets.backend.agendamiento.infraestructura.CitaRepository
import cl.clinipets.backend.servicios.dominio.ServicioMedico
import cl.clinipets.backend.servicios.infraestructura.ServicioMedicoRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class DisponibilidadServiceTest {

    private val citaRepository: CitaRepository = mock()
    private val servicioRepository: ServicioMedicoRepository = mock()
    private val disponibilidadService = DisponibilidadService(citaRepository, servicioRepository)

    @Test
    fun `debe retornar slots disponibles en horario valido`() {
        // Lunes: 11:00 a 13:00
        val fecha = LocalDate.of(2025, 11, 24) // Lunes
        val servicioId = 1L
        val servicioMock = ServicioMedico(id = servicioId, nombre = "Test", duracionMinutos = 30)

        whenever(servicioRepository.findById(servicioId)).thenReturn(Optional.of(servicioMock))
        whenever(citaRepository.findCitasActivasEntre(any(), any())).thenReturn(emptyList())

        val slots = disponibilidadService.obtenerSlotsDisponibles(fecha, servicioId)

        // Esperados (15 min steps, 30 min duration): 
        // 11:00 (ok, ends 11:30)
        // 11:15 (ok, ends 11:45)
        // 11:30 (ok, ends 12:00)
        // 11:45 (ok, ends 12:15)
        // 12:00 (ok, ends 12:30)
        // 12:15 (ok, ends 12:45)
        // 12:30 (ok, ends 13:00)
        // 12:45 (FAIL, ends 13:15 > 13:00)

        // Should match the expected slots based on 15 min intervals
        assertTrue(slots.contains(LocalTime.of(11, 0)))
        assertTrue(slots.contains(LocalTime.of(12, 30)))
        assertFalse(slots.contains(LocalTime.of(12, 45)))
    }

    @Test
    fun `debe filtrar slots ocupados`() {
        val fecha = LocalDate.of(2025, 11, 24) // Lunes
        val servicioId = 1L
        val servicioMock = ServicioMedico(id = servicioId, nombre = "Test", duracionMinutos = 30)

        val servicioCita = ServicioMedico(id = 2L, nombre = "Otro", duracionMinutos = 30)
        val citaOcupada = Cita(
            id = 1L,
            fechaHora = LocalDateTime.of(fecha, LocalTime.of(11, 30)),
            estado = EstadoCita.CONFIRMADA,
            mascota = mock(),
            servicioMedico = servicioCita,
            precioFinal = 10000,
            origen = OrigenCita.MANUAL
        )

        whenever(servicioRepository.findById(servicioId)).thenReturn(Optional.of(servicioMock))
        whenever(citaRepository.findCitasActivasEntre(any(), any())).thenReturn(listOf(citaOcupada))

        val slots = disponibilidadService.obtenerSlotsDisponibles(fecha, servicioId)

        // Cita ocupa 11:30 - 12:00.
        // Slot 11:00 (fin 11:30) -> OK (si termina justo cuando empieza la otra)
        // Slot 11:15 (fin 11:45) -> CHOQUE (termina dentro de la cita)
        // Slot 11:30 (fin 12:00) -> CHOQUE
        // Slot 11:45 (fin 12:15) -> CHOQUE (empieza dentro de la cita)
        // Slot 12:00 (fin 12:30) -> OK (empieza justo cuando termina la otra)

        assertTrue(slots.contains(LocalTime.of(11, 0)))
        assertFalse(slots.contains(LocalTime.of(11, 15)))
        assertFalse(slots.contains(LocalTime.of(11, 30)))
        assertTrue(slots.contains(LocalTime.of(12, 0)))
    }

    @Test
    fun `debe retornar vacio en domingo`() {
        val fecha = LocalDate.of(2025, 11, 30) // Domingo
        val servicioId = 1L
        val servicioMock = ServicioMedico(id = servicioId, nombre = "Test", duracionMinutos = 30)

        whenever(servicioRepository.findById(servicioId)).thenReturn(Optional.of(servicioMock))

        val slots = disponibilidadService.obtenerSlotsDisponibles(fecha, servicioId)
        assertTrue(slots.isEmpty())
    }
}

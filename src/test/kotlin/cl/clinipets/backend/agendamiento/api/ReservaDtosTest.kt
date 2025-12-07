package cl.clinipets.backend.agendamiento.api

import cl.clinipets.agendamiento.api.*
import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.DetalleCita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Mascota
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class ReservaDtosTest {

    @Test
    fun `ReservaCreateRequest should be correctly instantiated`() {
        val servicioId = UUID.randomUUID()
        val mascotaId = UUID.randomUUID()
        val detalle = DetalleReservaRequest(servicioId, mascotaId)
        
        val fechaHoraInicio = Instant.now()
        val origen = OrigenCita.WEB

        val request = ReservaCreateRequest(listOf(detalle), fechaHoraInicio, origen)

        assertEquals(1, request.detalles.size)
        assertEquals(servicioId, request.detalles[0].servicioId)
        assertEquals(mascotaId, request.detalles[0].mascotaId)
        assertEquals(fechaHoraInicio, request.fechaHoraInicio)
        assertEquals(origen, request.origen)
    }

    @Test
    fun `CitaResponse should be correctly instantiated`() {
        val id = UUID.randomUUID()
        val fechaHoraInicio = Instant.now()
        val fechaHoraFin = fechaHoraInicio.plus(1, ChronoUnit.HOURS)
        val estado = EstadoCita.PENDIENTE_PAGO
        val precioFinal = 10000
        val tutorId = UUID.randomUUID()
        val origen = OrigenCita.WEB
        val paymentUrl = "http://payment.url"
        
        val detalleResponse = DetalleCitaResponse(
            id = UUID.randomUUID(),
            servicioId = UUID.randomUUID(),
            nombreServicio = "Servicio Test",
            mascotaId = UUID.randomUUID(),
            nombreMascota = "Mascota Test",
            precioUnitario = 5000
        )

        val response = CitaResponse(
            id,
            fechaHoraInicio,
            fechaHoraFin,
            estado,
            precioFinal,
            0, // montoAbono
            precioFinal, // saldoPendiente
            listOf(detalleResponse),
            tutorId,
            origen,
            cl.clinipets.agendamiento.domain.TipoAtencion.CLINICA, // tipoAtencion
            null, // direccion
            paymentUrl
        )

        assertEquals(id, response.id)
        assertEquals(fechaHoraInicio, response.fechaHoraInicio)
        assertEquals(fechaHoraFin, response.fechaHoraFin)
        assertEquals(estado, response.estado)
        assertEquals(precioFinal, response.precioFinal)
        assertEquals(1, response.detalles.size)
        assertEquals("Servicio Test", response.detalles[0].nombreServicio)
        assertEquals(tutorId, response.tutorId)
        assertEquals(origen, response.origen)
        assertEquals(paymentUrl, response.paymentUrl)
    }

    @Test
    fun `Cita toResponse should map correctly with paymentUrl`() {
        // Setup Mocks
        val servicioMock = mock(ServicioMedico::class.java)
        `when`(servicioMock.id).thenReturn(UUID.randomUUID())
        `when`(servicioMock.nombre).thenReturn("Consulta")
        
        val mascotaMock = mock(Mascota::class.java)
        `when`(mascotaMock.id).thenReturn(UUID.randomUUID())
        `when`(mascotaMock.nombre).thenReturn("Firulais")

        val citaId = UUID.randomUUID()
        val fechaHoraInicio = Instant.now()
        val fechaHoraFin = fechaHoraInicio.plus(1, ChronoUnit.HOURS)
        val estado = EstadoCita.PENDIENTE_PAGO
        val precioFinal = 15000
        val tutorId = UUID.randomUUID()
        val origen = OrigenCita.APP

        // Create Cita
        val cita = Cita(
            id = citaId,
            fechaHoraInicio = fechaHoraInicio,
            fechaHoraFin = fechaHoraFin,
            estado = estado,
            precioFinal = precioFinal,
            tutorId = tutorId,
            origen = origen
        )
        
        // Create Detalle linked to Cita
        val detalle = DetalleCita(
            id = UUID.randomUUID(),
            cita = cita,
            servicio = servicioMock,
            mascota = mascotaMock,
            precioUnitario = 15000
        )
        cita.detalles.add(detalle)

        val paymentUrl = "http://mock.payment.url"
        val response = cita.toResponse(paymentUrl)

        assertEquals(citaId, response.id)
        assertEquals(fechaHoraInicio, response.fechaHoraInicio)
        assertEquals(fechaHoraFin, response.fechaHoraFin)
        assertEquals(estado, response.estado)
        assertEquals(precioFinal, response.precioFinal)
        assertEquals(1, response.detalles.size)
        assertEquals(servicioMock.id, response.detalles[0].servicioId)
        assertEquals(mascotaMock.id, response.detalles[0].mascotaId)
        assertEquals(tutorId, response.tutorId)
        assertEquals(origen, response.origen)
        assertEquals(paymentUrl, response.paymentUrl)
    }
}

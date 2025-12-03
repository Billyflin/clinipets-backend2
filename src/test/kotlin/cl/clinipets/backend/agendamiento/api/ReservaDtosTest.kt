package cl.clinipets.backend.agendamiento.api

import cl.clinipets.agendamiento.api.CitaResponse
import cl.clinipets.agendamiento.api.ReservaCreateRequest
import cl.clinipets.agendamiento.api.toResponse
import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ReservaDtosTest {

    @Test
    fun `ReservaCreateRequest should be correctly instantiated`() {
        val servicioId = UUID.randomUUID()
        val mascotaId = UUID.randomUUID()
        val fechaHoraInicio = Instant.now()
        val origen = OrigenCita.WEB

        val request = ReservaCreateRequest(servicioId, mascotaId, fechaHoraInicio, origen)

        assertEquals(servicioId, request.servicioId)
        assertEquals(mascotaId, request.mascotaId)
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
        val servicioId = UUID.randomUUID()
        val mascotaId = UUID.randomUUID()
        val tutorId = UUID.randomUUID()
        val origen = OrigenCita.WEB
        val paymentUrl = "http://payment.url"

        val response = CitaResponse(
            id,
            fechaHoraInicio,
            fechaHoraFin,
            estado,
            precioFinal,
            servicioId,
            mascotaId,
            tutorId,
            origen,
            paymentUrl
        )

        assertEquals(id, response.id)
        assertEquals(fechaHoraInicio, response.fechaHoraInicio)
        assertEquals(fechaHoraFin, response.fechaHoraFin)
        assertEquals(estado, response.estado)
        assertEquals(precioFinal, response.precioFinal)
        assertEquals(servicioId, response.servicioId)
        assertEquals(mascotaId, response.mascotaId)
        assertEquals(tutorId, response.tutorId)
        assertEquals(origen, response.origen)
        assertEquals(paymentUrl, response.paymentUrl)
    }

    @Test
    fun `Cita toResponse should map correctly with paymentUrl`() {
        val citaId = UUID.randomUUID()
        val fechaHoraInicio = Instant.now()
        val fechaHoraFin = fechaHoraInicio.plus(1, ChronoUnit.HOURS)
        val estado = EstadoCita.PENDIENTE_PAGO
        val precioFinal = 15000
        val servicioId = UUID.randomUUID()
        val mascotaId = UUID.randomUUID()
        val tutorId = UUID.randomUUID()
        val origen = OrigenCita.APP

        val mockCita = mock(Cita::class.java)
        `when`(mockCita.id).thenReturn(citaId)
        `when`(mockCita.fechaHoraInicio).thenReturn(fechaHoraInicio)
        `when`(mockCita.fechaHoraFin).thenReturn(fechaHoraFin)
        `when`(mockCita.estado).thenReturn(estado)
        `when`(mockCita.precioFinal).thenReturn(precioFinal)
        `when`(mockCita.servicioId).thenReturn(servicioId)
        `when`(mockCita.mascotaId).thenReturn(mascotaId)
        `when`(mockCita.tutorId).thenReturn(tutorId)
        `when`(mockCita.origen).thenReturn(origen)

        val paymentUrl = "http://mock.payment.url"
        val response = mockCita.toResponse(paymentUrl)

        assertEquals(citaId, response.id)
        assertEquals(fechaHoraInicio, response.fechaHoraInicio)
        assertEquals(fechaHoraFin, response.fechaHoraFin)
        assertEquals(estado, response.estado)
        assertEquals(precioFinal, response.precioFinal)
        assertEquals(servicioId, response.servicioId)
        assertEquals(mascotaId, response.mascotaId)
        assertEquals(tutorId, response.tutorId)
        assertEquals(origen, response.origen)
        assertEquals(paymentUrl, response.paymentUrl)
    }

    @Test
    fun `Cita toResponse should map correctly without paymentUrl`() {
        val citaId = UUID.randomUUID()
        val fechaHoraInicio = Instant.now()
        val fechaHoraFin = fechaHoraInicio.plus(1, ChronoUnit.HOURS)
        val estado = EstadoCita.CONFIRMADA
        val precioFinal = 12000
        val servicioId = UUID.randomUUID()
        val mascotaId = UUID.randomUUID()
        val tutorId = UUID.randomUUID()
        val origen = OrigenCita.WHATSAPP

        val mockCita = mock(Cita::class.java)
        `when`(mockCita.id).thenReturn(citaId)
        `when`(mockCita.fechaHoraInicio).thenReturn(fechaHoraInicio)
        `when`(mockCita.fechaHoraFin).thenReturn(fechaHoraFin)
        `when`(mockCita.estado).thenReturn(estado)
        `when`(mockCita.precioFinal).thenReturn(precioFinal)
        `when`(mockCita.servicioId).thenReturn(servicioId)
        `when`(mockCita.mascotaId).thenReturn(mascotaId)
        `when`(mockCita.tutorId).thenReturn(tutorId)
        `when`(mockCita.origen).thenReturn(origen)

        val response = mockCita.toResponse() // No paymentUrl provided

        assertEquals(citaId, response.id)
        assertEquals(fechaHoraInicio, response.fechaHoraInicio)
        assertEquals(fechaHoraFin, response.fechaHoraFin)
        assertEquals(estado, response.estado)
        assertEquals(precioFinal, response.precioFinal)
        assertEquals(servicioId, response.servicioId)
        assertEquals(mascotaId, response.mascotaId)
        assertEquals(tutorId, response.tutorId)
        assertEquals(origen, response.origen)
        assertEquals(null, response.paymentUrl) // paymentUrl should be null
    }
}

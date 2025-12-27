package cl.clinipets.agendamiento.scheduler

import cl.clinipets.agendamiento.domain.*
import cl.clinipets.core.notifications.NotificationService
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

class RecordatorioSchedulerTest {

    private val citaRepository = mock<CitaRepository>()
    private val notificationService = mock<NotificationService>()
    private val clinicZoneId = ZoneId.of("America/Santiago")
    private val scheduler = RecordatorioScheduler(citaRepository, notificationService, clinicZoneId)

    private fun createTutor() = User(
        id = UUID.randomUUID(),
        email = "tutor@test.com",
        name = "Tutor Test",
        passwordHash = "hash",
        role = UserRole.CLIENT
    )

    @Test
    fun `enviarRecordatoriosDiarios captura citas en el rango de 24 a 48 horas`() {
        val ahora = Instant.now()
        val en30Horas = ahora.plus(30, ChronoUnit.HOURS)

        val tutor = createTutor()
        val cita = Cita(
            id = UUID.randomUUID(),
            tutor = tutor,
            fechaHoraInicio = en30Horas,
            fechaHoraFin = en30Horas.plus(30, ChronoUnit.MINUTES),
            estado = EstadoCita.CONFIRMADA,
            precioFinal = BigDecimal("10000"),
            origen = OrigenCita.APP
        )

        whenever(citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(any(), any()))
            .thenReturn(listOf(cita))

        scheduler.enviarRecordatoriosDiarios()

        verify(notificationService).enviarNotificacion(eq(tutor.id!!), any(), any(), any())
    }

    @Test
    fun `enviarRecordatoriosUrgentes ahora cubre una ventana de 2 horas para evitar gaps`() {
        val ahora = Instant.now()
        val citaEn95Min = ahora.plus(95, ChronoUnit.MINUTES)

        val tutor = createTutor()
        val cita = Cita(
            id = UUID.randomUUID(),
            tutor = tutor,
            fechaHoraInicio = citaEn95Min,
            fechaHoraFin = citaEn95Min.plus(30, ChronoUnit.MINUTES),
            estado = EstadoCita.CONFIRMADA,
            precioFinal = BigDecimal("10000"),
            origen = OrigenCita.APP
        )

        whenever(citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(any(), any()))
            .thenReturn(listOf(cita))

        scheduler.enviarRecordatoriosUrgentes()
        verify(notificationService).enviarNotificacion(eq(tutor.id!!), any(), any(), any())
    }
}

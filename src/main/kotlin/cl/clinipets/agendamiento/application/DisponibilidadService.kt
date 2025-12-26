package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.BloqueoAgenda
import cl.clinipets.agendamiento.domain.BloqueoAgendaRepository
import cl.clinipets.core.config.ClinicProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class DisponibilidadService(
    private val citaRepository: CitaRepository,
    private val bloqueoAgendaRepository: BloqueoAgendaRepository,
    private val clinicProperties: ClinicProperties,
    private val clinicZoneId: ZoneId,
    private val inventarioService: cl.clinipets.servicios.application.InventarioService
) {
    private val logger = LoggerFactory.getLogger(DisponibilidadService::class.java)
    private val intervaloMinutos = 15L
    private val bufferMinutosMismoDia = 60L

    @Transactional(readOnly = true)
    fun obtenerSlots(fecha: LocalDate, duracionMinutos: Int, servicioId: UUID? = null): List<Instant> {
        logger.info(">>> Calculando disponibilidad. Fecha: $fecha, Duración: $duracionMinutos min, Zona: $clinicZoneId")

        // 1. Validar stock primero si se especificó servicio
        if (servicioId != null) {
            val hayStock = inventarioService.validarDisponibilidadReserva(servicioId, 1)
            if (!hayStock) {
                logger.warn(">>> Servicio $servicioId no tiene stock disponible (considerando reservas)")
                return emptyList()
            }
        }

        val ventana = obtenerVentanaPara(fecha)

        if (ventana == null) {
            logger.warn(">>> La clínica está CERRADA los ${fecha.dayOfWeek}")
            return emptyList()
        }

        val (abre, cierra) = ventana
        logger.info(">>> Horario del día: $abre a $cierra")

        val startOfDay = fecha.atStartOfDay(clinicZoneId).toInstant()
        val endOfDay = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()
        val ahora = Instant.now()
        val limiteMinimo = if (fecha == LocalDate.now(clinicZoneId)) {
            ahora.plus(bufferMinutosMismoDia, ChronoUnit.MINUTES)
        } else {
            startOfDay
        }

        val citasDia = citaRepository.findOverlappingCitas(startOfDay, endOfDay)
        val bloqueosDia = bloqueoAgendaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(startOfDay, endOfDay)

        val slots = mutableListOf<Instant>()
        var cursor = fecha.atTime(abre).atZone(clinicZoneId).toInstant()
        val limiteCierre = fecha.atTime(cierra).atZone(clinicZoneId).toInstant()

        while (cursor.plus(duracionMinutos.toLong(), ChronoUnit.MINUTES) <= limiteCierre) {
            if (cursor >= limiteMinimo) {
                val inicio = cursor
                val fin = inicio.plus(duracionMinutos.toLong(), ChronoUnit.MINUTES)

                if (estaLibre(inicio, fin, citasDia, bloqueosDia)) {
                    slots.add(cursor)
                }
            }
            cursor = cursor.plus(intervaloMinutos, ChronoUnit.MINUTES)
        }

        return slots
    }

    private fun obtenerVentanaPara(fecha: LocalDate): Pair<LocalTime, LocalTime>? {
        val dayName = fecha.dayOfWeek.name
        val scheduleStr = clinicProperties.schedule[dayName] 
            ?: clinicProperties.schedule[dayName.lowercase()]
            ?: return null
        return try {
            val parts = scheduleStr.split("-")
            LocalTime.parse(parts[0]) to LocalTime.parse(parts[1])
        } catch (e: Exception) {
            logger.error("Error parseando horario para ${fecha.dayOfWeek}: $scheduleStr")
            null
        }
    }

    private fun estaLibre(inicio: Instant, fin: Instant, citas: List<Cita>, bloqueos: List<BloqueoAgenda>): Boolean {
        val chocaConCita = citas.any { cita ->
            inicio < cita.fechaHoraFin && fin > cita.fechaHoraInicio
        }
        val chocaConBloqueo = bloqueos.any { bloqueo ->
            inicio < bloqueo.fechaHoraFin && fin > bloqueo.fechaHoraInicio
        }
        return !chocaConCita && !chocaConBloqueo
    }
}
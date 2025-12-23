package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.BloqueoAgenda
import cl.clinipets.agendamiento.domain.BloqueoAgendaRepository
import cl.clinipets.agendamiento.domain.HorarioClinica
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class DisponibilidadService(
    private val citaRepository: CitaRepository,
    private val bloqueoAgendaRepository: BloqueoAgendaRepository,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(DisponibilidadService::class.java)
    private val intervaloMinutos = 15L

    @Transactional(readOnly = true)
    fun obtenerSlots(fecha: LocalDate, duracionMinutos: Int): List<Instant> {
        logger.info(">>> Calculando disponibilidad. Fecha: $fecha, Duración: $duracionMinutos min, Zona: $clinicZoneId")

        val ventana = HorarioClinica.ventanaPara(fecha.dayOfWeek)

        if (ventana == null) {
            logger.warn(">>> La clínica está CERRADA los ${fecha.dayOfWeek}")
            return emptyList()
        }

        val (abre, cierra) = ventana
        logger.info(">>> Horario del día: $abre a $cierra")

        // Calculamos rango del día completo para buscar citas existentes
        val startOfDay = fecha.atStartOfDay(clinicZoneId).toInstant()
        val endOfDay = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()

        logger.debug(">>> Buscando citas en DB entre $startOfDay y $endOfDay")

        val citasDia = citaRepository.findOverlappingCitas(startOfDay, endOfDay)
        logger.info(">>> Citas encontradas en conflicto: ${citasDia.size}")

        val bloqueosDia = bloqueoAgendaRepository.findByFechaHoraFinGreaterThanAndFechaHoraInicioLessThan(startOfDay, endOfDay)
        logger.info(">>> Bloqueos encontrados en conflicto: ${bloqueosDia.size}")

        val slots = mutableListOf<Instant>()

        // Construir el cursor inicial: fecha + hora de apertura
        var cursor = fecha.atTime(abre).atZone(clinicZoneId).toInstant()
        val limiteCierre = fecha.atTime(cierra).atZone(clinicZoneId).toInstant()

        logger.info(">>> Generando slots desde $cursor hasta $limiteCierre")

        while (cursor.plus(duracionMinutos.toLong(), ChronoUnit.MINUTES) <= limiteCierre) {
            val inicio = cursor
            val fin = inicio.plus(duracionMinutos.toLong(), ChronoUnit.MINUTES)

            if (estaLibre(inicio, fin, citasDia, bloqueosDia)) {
                slots.add(cursor)
                // Logueamos solo algunos para no saturar, o todos si estás depurando fuerte
                logger.debug("   -> Slot Disponible: $cursor ($inicio - $fin)")
            } else {
                logger.debug("   -> Slot OCUPADO: $cursor")
            }
            cursor = cursor.plus(intervaloMinutos, ChronoUnit.MINUTES)
        }

        logger.info(">>> Total slots disponibles calculados: ${slots.size}")
        return slots
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
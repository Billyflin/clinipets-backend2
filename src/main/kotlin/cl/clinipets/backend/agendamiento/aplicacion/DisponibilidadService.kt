package cl.clinipets.backend.agendamiento.aplicacion

import cl.clinipets.backend.agendamiento.dominio.HorarioClinica
import cl.clinipets.backend.agendamiento.infraestructura.CitaRepository
import cl.clinipets.backend.servicios.infraestructura.ServicioMedicoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Service
class DisponibilidadService(
    private val citaRepository: CitaRepository,
    private val servicioRepository: ServicioMedicoRepository
) {

    @Transactional(readOnly = true)
    fun obtenerSlotsDisponibles(fecha: LocalDate, servicioId: Long): List<LocalTime> {
        val servicio = servicioRepository.findById(servicioId)
            .orElseThrow { NoSuchElementException("Servicio no encontrado: $servicioId") }

        val duracionServicio = servicio.duracionMinutos.toLong()
        val horario = HorarioClinica.obtenerHorario(fecha.dayOfWeek) ?: return emptyList()

        val inicioDia = fecha.atStartOfDay()
        val finDia = fecha.plusDays(1).atStartOfDay()

        // Obtener citas y sus rangos ocupados
        val citasExistentes = citaRepository.findCitasActivasEntre(inicioDia, finDia)

        // Mapear a intervalos de tiempo ocupados (Inicio, Fin)
        // IMPORTANTE: La cita ocupa hasta Inicio + DuracionServicio de ESA cita.
        // PERO, mi modelo de Cita actual NO tiene duración guardada en la Cita (error de diseño original o simplificación).
        // DEBO obtener la duración del servicio de cada cita.
        // Optimización: Podría hacer un join fetch, pero por ahora iteramos.

        val bloquesOcupados = citasExistentes.map { cita ->
            val inicio = cita.fechaHora.toLocalTime()
            val fin = inicio.plusMinutes(cita.servicioMedico.duracionMinutos.toLong())
            InicioFin(inicio, fin)
        }

        val slots = mutableListOf<LocalTime>()
        var horaActual = horario.inicio
        val intervaloMinutos = 15L // Iteramos cada 15 min para encontrar huecos

        // Tetris Algorithm:
        // Buscamos si [horaActual, horaActual + duracionServicio] colisiona con algo.

        while (horaActual.plusMinutes(duracionServicio)
                .isBefore(horario.fin) || horaActual.plusMinutes(duracionServicio) == horario.fin
        ) {
            val finTentativo = horaActual.plusMinutes(duracionServicio)

            val colisiona = bloquesOcupados.any { ocupado ->
                // Hay colisión si se solapan los intervalos
                // (StartA < EndB) and (EndA > StartB)
                horaActual.isBefore(ocupado.fin) && finTentativo.isAfter(ocupado.inicio)
            }

            if (!colisiona) {
                slots.add(horaActual)
            }

            horaActual = horaActual.plusMinutes(intervaloMinutos)
        }

        return slots
    }

    data class InicioFin(val inicio: LocalTime, val fin: LocalTime)
}
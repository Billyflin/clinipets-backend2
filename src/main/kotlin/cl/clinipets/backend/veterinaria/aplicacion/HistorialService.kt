package cl.clinipets.backend.veterinaria.aplicacion

import cl.clinipets.backend.mascotas.infraestructura.MascotaRepository
import cl.clinipets.backend.veterinaria.dominio.EventoMedico
import cl.clinipets.backend.veterinaria.dominio.TipoEvento
import cl.clinipets.backend.veterinaria.infraestructura.EventoMedicoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@Service
class HistorialService(
    private val eventoRepo: EventoMedicoRepository,
    private val mascotaRepo: MascotaRepository
) {

    data class CrearEventoCommand(
        val mascotaId: UUID,
        val fecha: LocalDate,
        val tipo: TipoEvento,
        val descripcion: String,
        val fechaProximoEvento: LocalDate?,
        val usuarioResponsable: String?
    )

    @Transactional
    fun registrarEvento(cmd: CrearEventoCommand): EventoMedico {
        val mascota = mascotaRepo.findById(cmd.mascotaId)
            .orElseThrow { IllegalArgumentException("Mascota no encontrada con ID: ${cmd.mascotaId}") }

        val evento = EventoMedico(
            mascota = mascota,
            fecha = cmd.fecha,
            tipo = cmd.tipo,
            descripcion = cmd.descripcion,
            fechaProximoEvento = cmd.fechaProximoEvento,
            usuarioResponsable = cmd.usuarioResponsable
        )

        return eventoRepo.save(evento)
    }

    @Transactional(readOnly = true)
    fun obtenerHistorial(mascotaId: UUID): List<EventoMedico> {
        if (!mascotaRepo.existsById(mascotaId)) {
            throw IllegalArgumentException("Mascota no encontrada")
        }
        return eventoRepo.findByMascotaIdOrderByFechaDesc(mascotaId)
    }

    /**
     * Usado por n8n para obtener vacunas/controles que vencen pronto.
     * @param fechaLimite Típicamente "mañana" o "en 7 días".
     */
    @Transactional(readOnly = true)
    fun buscarRecordatorios(fechaLimite: LocalDate): List<EventoMedico> {
        val hoy = LocalDate.now()
        return eventoRepo.findByFechaProximoEventoBetween(hoy, fechaLimite)
    }
}

package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GestionAgendaService(
    private val citaRepository: CitaRepository
) {
    private val logger = LoggerFactory.getLogger(GestionAgendaService::class.java)

    @Transactional
    fun iniciarAtencion(citaId: UUID, staff: JwtPayload): Cita {
        logger.info("[INICIAR_ATENCION] Iniciando atención de cita $citaId por ${staff.email}")
        val cita = citaRepository.findById(citaId)
            .orElseThrow { NotFoundException("Cita no encontrada: $citaId") }

        cita.cambiarEstado(EstadoCita.EN_ATENCION, staff.email)
        val resultado = citaRepository.save(cita)
        
        logger.info("[INICIAR_ATENCION] Cita $citaId marcada como EN_ATENCION")
        return resultado
    }
}

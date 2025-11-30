package cl.clinipets.backend.servicios.application

import cl.clinipets.backend.servicios.api.ServicioMedicoDto
import cl.clinipets.backend.servicios.api.toDto
import cl.clinipets.backend.servicios.domain.ServicioMedicoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServicioMedicoService(
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    @Transactional(readOnly = true)
    fun listarActivos(): List<ServicioMedicoDto> =
        servicioMedicoRepository.findByActivoTrue().map { it.toDto() }
}

package cl.clinipets.servicios.application

import cl.clinipets.servicios.api.ServicioMedicoDto
import cl.clinipets.servicios.api.toDto
import cl.clinipets.servicios.domain.ServicioMedicoRepository
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

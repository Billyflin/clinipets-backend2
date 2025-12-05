package cl.clinipets.servicios.application

import cl.clinipets.servicios.api.ServicioMedicoDto
import cl.clinipets.servicios.api.toDto
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServicioMedicoService(
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    private val logger = LoggerFactory.getLogger(ServicioMedicoService::class.java)

    @Transactional(readOnly = true)
    fun listarActivos(): List<ServicioMedicoDto> {
        logger.debug("[SERVICIO_MEDICO] Buscando servicios activos")
        val servicios = servicioMedicoRepository.findByActivoTrue().map { it.toDto() }
        logger.debug("[SERVICIO_MEDICO] Encontrados {} servicios activos", servicios.size)
        return servicios
    }
}

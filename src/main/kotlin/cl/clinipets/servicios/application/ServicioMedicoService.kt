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
        val servicios = servicioMedicoRepository.findByActivoTrue()

        val filtrados = servicios.filter { servicio ->
            // 1. Verificar stock directo (si es un producto con stock definido)
            if (servicio.stock != null && servicio.stock!! <= 0) {
                return@filter false
            }

            // 2. Verificar stock de insumos críticos
            if (servicio.insumos.isEmpty()) return@filter true

            servicio.insumos.filter { it.critico }.all { si ->
                si.insumo.stockActual >= si.cantidadRequerida
            }
        }.map { it.toDto() }

        logger.debug(
            "[SERVICIO_MEDICO] Encontrados {} servicios activos ({} tras filtro de stock)",
            servicios.size,
            filtrados.size
        )
        return filtrados
    }
}

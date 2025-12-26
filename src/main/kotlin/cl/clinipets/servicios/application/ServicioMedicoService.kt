package cl.clinipets.servicios.application

import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.api.ServicioMedicoDto
import cl.clinipets.servicios.api.toDto
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ServicioMedicoService(
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    private val logger = LoggerFactory.getLogger(ServicioMedicoService::class.java)

    @Transactional
    @CacheEvict(value = ["servicios"], allEntries = true)
    fun actualizarDependencias(servicioId: UUID, nuevosIdsRequeridos: Set<UUID>): ServicioMedicoDto {
        logger.info("[SERVICIO_MEDICO] Actualizando dependencias para servicio: {}", servicioId)
        
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        val nuevasDependencias = servicioMedicoRepository.findAllById(nuevosIdsRequeridos).toSet()
        if (nuevasDependencias.size < nuevosIdsRequeridos.size) {
            throw NotFoundException("Algunos servicios requeridos no fueron encontrados")
        }

        // Simular el cambio temporalmente para validar ciclos
        val dependenciasOriginales = servicio.serviciosRequeridos.toSet()
        servicio.serviciosRequeridos.clear()
        servicio.serviciosRequeridos.addAll(nuevasDependencias)

        try {
            validarCiclos(servicio)
        } catch (e: ConflictException) {
            // Revertir en caso de error (aunque @Transactional lo haría, es mejor dejar el objeto limpio)
            servicio.serviciosRequeridos.clear()
            servicio.serviciosRequeridos.addAll(dependenciasOriginales)
            throw e
        }

        val guardado = servicioMedicoRepository.save(servicio)
        logger.info("[SERVICIO_MEDICO] Dependencias actualizadas exitosamente para {}", servicio.nombre)
        return guardado.toDto()
    }

    private fun validarCiclos(servicioRaiz: ServicioMedico) {
        val visitados = mutableSetOf<UUID>()
        val enRecorridoActual = mutableSetOf<UUID>()

        fun dfs(actual: ServicioMedico) {
            val id = actual.id!!
            visitados.add(id)
            enRecorridoActual.add(id)

            for (dependencia in actual.serviciosRequeridos) {
                val depId = dependencia.id!!
                if (depId in enRecorridoActual) {
                    logger.error("[SERVICIO_MEDICO] Ciclo detectado: {} depende de sí mismo a través del grafo", actual.nombre)
                    throw ConflictException("Ciclo de dependencia detectado: ${actual.nombre} -> ${dependencia.nombre}")
                }
                if (depId !in visitados) {
                    dfs(dependencia)
                }
            }
            enRecorridoActual.remove(id)
        }

        dfs(servicioRaiz)
    }

    @Transactional(readOnly = true)
    @Cacheable(value = ["servicios"])
    fun listarActivos(): List<ServicioMedicoDto> {
        logger.info("[CACHE_MISS] Cargando servicios desde DB")
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

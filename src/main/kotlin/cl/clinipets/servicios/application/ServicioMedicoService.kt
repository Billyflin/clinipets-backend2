package cl.clinipets.servicios.application

import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.api.*
import cl.clinipets.servicios.domain.*
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ServicioMedicoService(
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val insumoRepository: InsumoRepository,
    private val promocionRepository: PromocionRepository
) {
    private val logger = LoggerFactory.getLogger(ServicioMedicoService::class.java)

    @Transactional
    @CacheEvict(value = ["servicios"], allEntries = true)
    fun crear(request: ServicioCreateRequest): ServicioMedicoDto {
        logger.info("[SERVICIO_MEDICO] Creando nuevo servicio: {}", request.nombre)

        if (servicioMedicoRepository.existsByNombreIgnoreCase(request.nombre)) {
            throw ConflictException("Ya existe un servicio con el nombre: ${request.nombre}")
        }

        val servicio = ServicioMedico(
            nombre = request.nombre,
            precioBase = request.precioBase,
            precioAbono = request.precioAbono,
            requierePeso = request.requierePeso,
            duracionMinutos = request.duracionMinutos,
            categoria = request.categoria,
            especiesPermitidas = request.especiesPermitidas.toMutableSet(),
            stock = request.stock,
            bloqueadoSiEsterilizado = request.bloqueadoSiEsterilizado,
            actualizaMarcador = request.actualizaMarcador,
            condicionMarcadorClave = request.condicionMarcadorClave,
            condicionMarcadorValor = request.condicionMarcadorValor
        )

        // Manejar dependencias
        if (request.serviciosRequeridosIds.isNotEmpty()) {
            val dependencias = servicioMedicoRepository.findAllById(request.serviciosRequeridosIds)
            servicio.serviciosRequeridos.addAll(dependencias)
        }

        // Manejar reglas de precio
        request.reglas.forEach { r ->
            servicio.reglas.add(
                ReglaPrecio(
                    pesoMin = r.pesoMin,
                    pesoMax = r.pesoMax,
                    precio = r.precio,
                    servicio = servicio
                )
            )
        }

        // Manejar insumos
        request.insumos.forEach { i ->
            val insumo = insumoRepository.findById(i.insumoId)
                .orElseThrow { NotFoundException("Insumo no encontrado: ${i.insumoId}") }
            servicio.insumos.add(
                ServicioInsumo(
                    servicio = servicio,
                    insumo = insumo,
                    cantidadRequerida = i.cantidadRequerida,
                    critico = i.critico
                )
            )
        }

        val guardado = servicioMedicoRepository.save(servicio)
        validarCiclos(guardado) // Validar ciclos después de guardar (o antes si se prefiere)

        return guardado.toDto()
    }

    @Transactional
    @CacheEvict(value = ["servicios"], allEntries = true)
    fun actualizar(id: UUID, request: ServicioUpdateRequest): ServicioMedicoDto {
        logger.info("[SERVICIO_MEDICO] Actualizando servicio: {}", id)

        val servicio = servicioMedicoRepository.findById(id)
            .orElseThrow { NotFoundException("Servicio no encontrado: $id") }

        request.nombre?.let {
            if (it != servicio.nombre && servicioMedicoRepository.existsByNombreIgnoreCase(it)) {
                throw ConflictException("Ya existe un servicio con el nombre: $it")
            }
            servicio.nombre = it
        }
        request.precioBase?.let { servicio.precioBase = it }
        request.precioAbono?.let { servicio.precioAbono = it }
        request.requierePeso?.let { servicio.requierePeso = it }
        request.duracionMinutos?.let { servicio.duracionMinutos = it }
        request.activo?.let {
            if (!it) {
                // Al desactivar, validar promociones asociadas
                val promosRelacionadas = promocionRepository.findAllByActivaTrue()
                    .filter { p -> p.serviciosTrigger.any { s -> s.id == id } || p.beneficios.any { b -> b.servicio.id == id } }
                if (promosRelacionadas.isNotEmpty()) {
                    logger.warn(
                        "[SERVICIO_MEDICO] Desactivando servicio {} con promociones activas asociadas: {}",
                        id,
                        promosRelacionadas.map { it.nombre })
                }
            }
            servicio.activo = it
        }
        request.categoria?.let { servicio.categoria = it }
        request.especiesPermitidas?.let {
            servicio.especiesPermitidas.clear()
            servicio.especiesPermitidas.addAll(it)
        }
        request.stock?.let { servicio.stock = it }
        request.bloqueadoSiEsterilizado?.let { servicio.bloqueadoSiEsterilizado = it }
        request.actualizaMarcador?.let { servicio.actualizaMarcador = it }
        request.condicionMarcadorClave?.let { servicio.condicionMarcadorClave = it }
        request.condicionMarcadorValor?.let { servicio.condicionMarcadorValor = it }

        return servicioMedicoRepository.save(servicio).toDto()
    }

    @Transactional
    @CacheEvict(value = ["servicios"], allEntries = true)
    fun eliminar(id: UUID) {
        logger.info("[SERVICIO_MEDICO] Intentando eliminar servicio: {}", id)
        val servicio = servicioMedicoRepository.findById(id)
            .orElseThrow { NotFoundException("Servicio no encontrado: $id") }

        // Soft delete: simplemente desactivamos
        servicio.activo = false
        servicioMedicoRepository.save(servicio)
        logger.info("[SERVICIO_MEDICO] Servicio {} marcado como inactivo (Soft Delete)", id)
    }

    @Transactional
    @CacheEvict(value = ["servicios"], allEntries = true)
    fun agregarRegla(servicioId: UUID, request: ReglaPrecioRequest): ServicioMedicoDto {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        servicio.reglas.add(
            ReglaPrecio(
                pesoMin = request.pesoMin,
                pesoMax = request.pesoMax,
                precio = request.precio,
                servicio = servicio
            )
        )

        return servicioMedicoRepository.save(servicio).toDto()
    }

    @Transactional
    @CacheEvict(value = ["servicios"], allEntries = true)
    fun eliminarRegla(servicioId: UUID, reglaId: UUID): ServicioMedicoDto {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        if (!servicio.reglas.removeIf { it.id == reglaId }) {
            throw NotFoundException("Regla de precio no encontrada en el servicio")
        }

        return servicioMedicoRepository.save(servicio).toDto()
    }

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

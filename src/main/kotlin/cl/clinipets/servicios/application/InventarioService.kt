package cl.clinipets.servicios.application

import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.api.*
import cl.clinipets.servicios.domain.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@Service
class InventarioService(
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val insumoRepository: InsumoRepository,
    private val citaRepository: cl.clinipets.agendamiento.domain.CitaRepository,
    private val loteInsumoRepository: LoteInsumoRepository,
    private val servicioInsumoRepository: ServicioInsumoRepository
) {
    private val logger = LoggerFactory.getLogger(InventarioService::class.java)

    @Transactional(readOnly = true)
    fun listarInsumos(): List<InsumoResponse> {
        return insumoRepository.findAll().map { it.toResponse() }
    }

    @Transactional
    fun crearInsumo(request: InsumoCreateRequest): InsumoResponse {
        logger.info("[INVENTARIO] Creando nuevo insumo: {}", request.nombre)
        val insumo = Insumo(
            nombre = request.nombre,
            stockActual = 0.0,
            stockMinimo = request.stockMinimo,
            unidadMedida = request.unidadMedida,
            contraindicacionMarcador = request.contraindicacionMarcador
        )
        return insumoRepository.save(insumo).toResponse()
    }

    @Transactional
    fun actualizarInsumo(id: UUID, request: InsumoUpdateRequest): InsumoResponse {
        logger.info("[INVENTARIO] Actualizando insumo: {}", id)
        val insumo = insumoRepository.findById(id)
            .orElseThrow { NotFoundException("Insumo no encontrado: $id") }

        request.nombre?.let { insumo.nombre = it }
        request.stockMinimo?.let { insumo.stockMinimo = it }
        request.unidadMedida?.let { insumo.unidadMedida = it }
        request.contraindicacionMarcador?.let { insumo.contraindicacionMarcador = it }

        return insumoRepository.save(insumo).toResponse()
    }

    @Transactional
    fun eliminarInsumo(id: UUID) {
        logger.info("[INVENTARIO] Intentando eliminar insumo: {}", id)
        val insumo = insumoRepository.findById(id)
            .orElseThrow { NotFoundException("Insumo no encontrado: $id") }

        // Validar si está en uso por servicios activos
        val enUso = servicioInsumoRepository.findByInsumoId(id).any { it.servicio.activo }
        if (enUso) {
            throw ConflictException("No se puede eliminar el insumo porque está siendo utilizado por servicios activos")
        }

        insumoRepository.delete(insumo)
        logger.info("[INVENTARIO] Insumo {} eliminado", id)
    }

    @Transactional
    fun agregarLote(insumoId: UUID, request: LoteCreateRequest): InsumoResponse {
        logger.info("[INVENTARIO] Agregando lote {} al insumo {}", request.codigoLote, insumoId)
        val insumo = insumoRepository.findByIdWithLock(insumoId)
            ?: throw NotFoundException("Insumo no encontrado: $insumoId")

        val nuevoLote = LoteInsumo(
            insumo = insumo,
            codigoLote = request.codigoLote,
            fechaVencimiento = request.fechaVencimiento,
            cantidadInicial = request.cantidad,
            cantidadActual = request.cantidad
        )

        insumo.lotes.add(nuevoLote)
        insumo.stockActual += request.cantidad

        return insumoRepository.save(insumo).toResponse()
    }

    @Transactional
    fun ajustarStockLote(loteId: UUID, request: LoteStockAjusteRequest): InsumoResponse {
        logger.info(
            "[INVENTARIO] Ajustando stock de lote {}. Nueva cantidad: {}. Motivo: {}",
            loteId,
            request.nuevaCantidad,
            request.motivo
        )
        val lote = loteInsumoRepository.findById(loteId)
            .orElseThrow { NotFoundException("Lote no encontrado: $loteId") }

        val diferencia = request.nuevaCantidad - lote.cantidadActual
        lote.cantidadActual = request.nuevaCantidad

        val insumo = lote.insumo
        insumo.stockActual += diferencia

        loteInsumoRepository.save(lote)
        return insumoRepository.save(insumo).toResponse()
    }

    /**
     * Valida si hay stock disponible considerando las citas ya reservadas (No pagadas).
     * Y que los insumos no estén vencidos.
     */
    @Transactional(readOnly = true)
    fun validarDisponibilidadReserva(servicioId: UUID, cantidad: Int): Boolean {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        // 1. Validar stock directo del servicio si lo tiene
        if (servicio.stock != null) {
            val reservados = citaRepository.countReservedStock(servicioId)
            val disponibleReal = (servicio.stock!! - reservados).coerceAtLeast(0)
            if (disponibleReal < cantidad) return false
        }

        // 2. Validar insumos críticos por lotes vigentes
        servicio.insumos.forEach { si ->
            if (si.critico) {
                val insumo = insumoRepository.findById(si.insumo.id!!).get()
                val totalRequerido = si.cantidadRequerida * cantidad
                if (insumo.stockVigente() < totalRequerido) {
                    logger.warn("[INVENTARIO] Insumo crítico ${insumo.nombre} no tiene stock VIGENTE suficiente.")
                    return false
                }
            }
        }

        return true
    }

    /**
     * Valida si hay stock disponible para un servicio sin realizar ninguna operación de escritura.
     * Considera solo lotes no vencidos.
     */
    @Transactional(readOnly = true)
    fun validarDisponibilidadStock(servicioId: UUID, cantidad: Int): Boolean {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        logger.info("[INVENTARIO] Validando stock vigente para ${servicio.nombre}")

        // 1. Validar stock directo del servicio
        if (servicio.stock != null) {
            if (servicio.stock!! < cantidad) {
                logger.warn("[INVENTARIO] Stock insuficiente: requiere $cantidad, disponible ${servicio.stock}")
                return false
            }
        }

        // 2. Validar insumos críticos (Mirando lotes)
        servicio.insumos.forEach { si ->
            val totalRequerido = si.cantidadRequerida * cantidad
            val insumo = insumoRepository.findById(si.insumo.id!!)
                .orElse(null) ?: return false

            if (insumo.stockVigente() < totalRequerido) {
                if (si.critico) {
                    logger.warn("[INVENTARIO] Stock VIGENTE insuficiente: ${insumo.nombre} requiere $totalRequerido")
                    return false
                }
            }
        }

        return true
    }

    /**
     * Consume stock de un servicio y sus insumos asociados con FEFO (First Expired, First Out).
     */
    @Transactional
    fun consumirStock(servicioId: UUID, cantidad: Int = 1, referencia: String) {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        logger.info("[INVENTARIO] Consumiendo stock (FEFO). Servicio: ${servicio.nombre}, Cantidad: $cantidad, Referencia: $referencia")

        // 1. Consumir stock directo del servicio
        if (servicio.stock != null) {
            if (servicio.stock!! < cantidad) {
                throw ConflictException("No hay stock suficiente para ${servicio.nombre}")
            }
            servicio.stock = servicio.stock!! - cantidad
            servicioMedicoRepository.save(servicio)
        }

        // 2. Consumir insumos asociados por LOTES (FEFO)
        servicio.insumos.forEach { si ->
            var remanente = si.cantidadRequerida * cantidad
            val insumo = insumoRepository.findByIdWithLock(si.insumo.id!!)
                ?: throw NotFoundException("Insumo no encontrado: ${si.insumo.id}")

            // Obtener lotes vigentes ordenados por vencimiento
            val lotesVigentes = loteInsumoRepository.findVigentesOrderByVencimiento(insumo.id!!)
            
            for (lote in lotesVigentes) {
                if (remanente <= 0) break
                
                val aConsumir = minOf(lote.cantidadActual, remanente)
                lote.cantidadActual -= aConsumir
                remanente -= aConsumir
                
                loteInsumoRepository.save(lote)
                logger.debug("[INVENTARIO] Consumido $aConsumir del lote ${lote.codigoLote} (Vence: ${lote.fechaVencimiento})")
            }

            if (remanente > 0 && si.critico) {
                throw ConflictException("No se pudo completar el consumo del insumo crítico ${insumo.nombre}. Faltaron $remanente unidades vigentes.")
            }

            // Actualizar stock plano para compatibilidad/alertas
            val consumido = (si.cantidadRequerida * cantidad) - remanente
            insumo.stockActual -= consumido
            insumoRepository.save(insumo)
        }
    }

    /**
     * Consume stock de un insumo específico (usado en recetas).
     */
    @Transactional
    fun consumirStockInsumo(insumoId: UUID, cantidad: Double, referencia: String) {
        val insumo = insumoRepository.findByIdWithLock(insumoId)
            ?: throw NotFoundException("Insumo no encontrado: $insumoId")

        logger.info("[INVENTARIO] Consumiendo insumo directo (Receta). Insumo: ${insumo.nombre}, Cantidad: $cantidad, Referencia: $referencia")

        var remanente = cantidad
        val lotesVigentes = loteInsumoRepository.findVigentesOrderByVencimiento(insumo.id!!)

        for (lote in lotesVigentes) {
            if (remanente <= 0.0) break

            val aConsumir = minOf(lote.cantidadActual, remanente)
            lote.cantidadActual -= aConsumir
            remanente -= aConsumir

            loteInsumoRepository.save(lote)
            logger.debug("[INVENTARIO] Consumido $aConsumir del lote ${lote.codigoLote}")
        }

        if (remanente > 0.0) {
            throw ConflictException("Stock insuficiente para el insumo ${insumo.nombre}. Faltaron $remanente vigentes.")
        }

        val consumido = cantidad - remanente
        insumo.stockActual -= consumido
        insumoRepository.save(insumo)
    }

    @Transactional
    fun devolverStock(servicio: ServicioMedico, cantidad: Int = 1) {
        val servicioFresco = servicioMedicoRepository.findById(servicio.id!!)
            .orElse(null) ?: return

        if (servicioFresco.stock != null) {
            servicioFresco.stock = servicioFresco.stock!! + cantidad
            servicioMedicoRepository.save(servicioFresco)
        }

        servicioFresco.insumos.forEach { si ->
            val insumo = insumoRepository.findByIdWithLock(si.insumo.id!!)
                ?: return@forEach

            val cantidadADevolver = si.cantidadRequerida * cantidad

            // Intentar devolver al primer lote vigente disponible
            val lotes = loteInsumoRepository.findVigentesOrderByVencimiento(insumo.id!!)
            if (lotes.isNotEmpty()) {
                val lote = lotes[0]
                lote.cantidadActual += cantidadADevolver
                loteInsumoRepository.save(lote)
            } else {
                // Si no hay lotes vigentes, creamos un lote de emergencia para devoluciones
                loteInsumoRepository.save(
                    LoteInsumo(
                        insumo = insumo,
                        codigoLote = "DEV-${UUID.randomUUID().toString().take(8)}",
                        fechaVencimiento = LocalDate.now().plusMonths(6),
                        cantidadInicial = cantidadADevolver,
                        cantidadActual = cantidadADevolver
                    )
                )
            }

            insumo.stockActual += cantidadADevolver
            insumoRepository.save(insumo)
        }
    }
}

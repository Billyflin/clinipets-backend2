package cl.clinipets.servicios.application

import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.domain.InsumoRepository
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class InventarioService(
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val insumoRepository: InsumoRepository
) {
    private val logger = LoggerFactory.getLogger(InventarioService::class.java)

    /**
     * Valida si hay stock disponible para un servicio sin realizar ninguna operación de escritura.
     * @return true si hay stock suficiente, false en caso contrario
     */
    @Transactional(readOnly = true)
    fun validarDisponibilidadStock(servicioId: UUID, cantidad: Int): Boolean {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        logger.info("[INVENTARIO] Validando stock para ${servicio.nombre}")

        // 1. Validar stock directo del servicio
        if (servicio.stock != null) {
            if (servicio.stock!! < cantidad) {
                logger.warn(
                    "[INVENTARIO] Stock insuficiente: requiere $cantidad, disponible ${servicio.stock}"
                )
                return false
            }
        }

        // 2. Validar insumos críticos
        servicio.insumos.forEach { si ->
            val totalRequerido = si.cantidadRequerida * cantidad
            val insumo = insumoRepository.findById(si.insumo.id!!)
                .orElse(null) ?: return false

            if (insumo.stockActual < totalRequerido) {
                if (si.critico) {
                    logger.warn(
                        "[INVENTARIO] Stock insuficiente: ${insumo.nombre} requiere $totalRequerido, disponible ${insumo.stockActual}"
                    )
                    return false
                } else {
                    logger.warn(
                        "[INVENTARIO] Insumo no crítico ${insumo.nombre} sin stock suficiente (requiere $totalRequerido, disponible ${insumo.stockActual})"
                    )
                }
            }
        }

        return true
    }

    /**
     * Consume stock de un servicio y sus insumos asociados con locks pesimistas.
     * @throws ConflictException si falta stock crítico
     */
    @Transactional
    fun consumirStock(servicioId: UUID, cantidad: Int = 1, referencia: String) {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        logger.info(
            "[INVENTARIO] Consumiendo stock e insumos. Servicio: ${servicio.nombre}, Cantidad: $cantidad, Referencia: $referencia"
        )

        // 1. Consumir stock directo del servicio (ej: si es un producto físico)
        if (servicio.stock != null) {
            if (servicio.stock!! < cantidad) {
                logger.warn(
                    "[INVENTARIO] Stock insuficiente: requiere $cantidad, disponible ${servicio.stock}"
                )
                throw ConflictException("No hay stock suficiente para ${servicio.nombre}")
            }
            servicio.stock = servicio.stock!! - cantidad
            servicioMedicoRepository.save(servicio)
            logger.debug("[INVENTARIO] Stock del servicio ${servicio.nombre} consumido: -$cantidad")
        }

        // 2. Consumir insumos asociados con PESSIMISTIC LOCK
        servicio.insumos.forEach { si ->
            val totalRequerido = si.cantidadRequerida * cantidad

            // Bloqueamos la fila del insumo para actualización segura
            val insumo = insumoRepository.findByIdWithLock(si.insumo.id!!)
                ?: throw NotFoundException("Insumo no encontrado: ${si.insumo.nombre}")

            if (insumo.stockActual < totalRequerido) {
                if (si.critico) {
                    logger.warn(
                        "[INVENTARIO] Stock insuficiente del insumo crítico ${insumo.nombre}: requiere $totalRequerido, disponible ${insumo.stockActual}"
                    )
                    throw ConflictException("No hay stock suficiente del insumo crítico: ${insumo.nombre}")
                } else {
                    logger.warn(
                        "[INVENTARIO] Insumo no crítico ${insumo.nombre} sin stock suficiente, continuando sin consumir"
                    )
                }
            } else {
                insumo.stockActual -= totalRequerido
                insumoRepository.save(insumo)
                logger.debug("[INVENTARIO] Insumo ${insumo.nombre} consumido: -$totalRequerido")
            }
        }
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
            insumo.stockActual += si.cantidadRequerida * cantidad
            insumoRepository.save(insumo)
        }
    }
}

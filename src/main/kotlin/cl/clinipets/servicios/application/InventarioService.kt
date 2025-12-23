package cl.clinipets.servicios.application

import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class InventarioService(
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    private val logger = LoggerFactory.getLogger(InventarioService::class.java)

    @Retryable(
        retryFor = [ObjectOptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 50)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun consumirStock(servicioId: UUID, cantidad: Int = 1, referencia: String) {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        if (servicio.stock == null) return

        logger.debug(
            "[INVENTARIO] Consumiendo stock. Servicio: {}, Cantidad: {}, Referencia: {}",
            servicio.nombre,
            cantidad,
            referencia
        )
        if (servicio.stock!! < cantidad) {
            logger.warn("[INVENTARIO] Stock insuficiente para {}. Solicitado: {}, Disponible: {}", servicio.nombre, cantidad, servicio.stock)
            throw ConflictException("No hay stock suficiente para ${servicio.nombre}")
        }
        
        servicio.stock = servicio.stock!! - cantidad
        try {
            servicioMedicoRepository.save(servicio)
        } catch (ex: ObjectOptimisticLockingFailureException) {
            logger.warn("[INVENTARIO_CONCURRENCIA] Falló la actualización de stock para {}. Se reintentará.", servicio.nombre)
            throw ex // Re-throw to trigger Retry
        }
        logger.info("Stock de {} reducido manualmente por {}", servicio.nombre, referencia)
    }

    @Retryable(
        retryFor = [ObjectOptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 50)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun devolverStock(servicio: ServicioMedico, cantidad: Int = 1) {
        // Recargamos la entidad para tener la versión más reciente
        val servicioFresco = servicioMedicoRepository.findById(servicio.id!!)
            .orElse(null)

        if (servicioFresco == null || servicioFresco.stock == null) return

        logger.debug(
            "[INVENTARIO] Devolviendo stock. Servicio: {}, Cantidad: {}, StockActual: {}",
            servicioFresco.nombre,
            cantidad,
            servicioFresco.stock
        )
        servicioFresco.stock = servicioFresco.stock!! + cantidad
        try {
            servicioMedicoRepository.save(servicioFresco)
        } catch (ex: ObjectOptimisticLockingFailureException) {
            logger.warn(
                "[INVENTARIO_CONCURRENCIA] Falló la devolución de stock para {}. Se reintentará.",
                servicioFresco.nombre
            )
            throw ex // Re-throw to trigger Retry
        }
        logger.info(
            "[INVENTARIO] Stock devuelto. Servicio: {}, NuevoStock: {}",
            servicioFresco.nombre,
            servicioFresco.stock
        )
    }
}

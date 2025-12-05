package cl.clinipets.servicios.application

import cl.clinipets.core.web.ConflictException
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventarioService(
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    private val logger = LoggerFactory.getLogger(InventarioService::class.java)

    @Transactional
    fun consumirStock(servicio: ServicioMedico, cantidad: Int = 1) {
        if (servicio.stock == null) return

        logger.debug("[INVENTARIO] Consumiendo stock. Servicio: {}, Cantidad: {}, StockActual: {}", servicio.nombre, cantidad, servicio.stock)
        if (servicio.stock!! < cantidad) {
            logger.warn("[INVENTARIO] Stock insuficiente para {}. Solicitado: {}, Disponible: {}", servicio.nombre, cantidad, servicio.stock)
            throw ConflictException("No hay stock suficiente para ${servicio.nombre}")
        }
        
        servicio.stock = servicio.stock!! - cantidad
        try {
            servicioMedicoRepository.save(servicio)
        } catch (ex: ObjectOptimisticLockingFailureException) {
            logger.warn("[INVENTARIO_CONCURRENCIA] Falló la actualización de stock para {}. Se reintentará.", servicio.nombre)
            throw ConflictException("El stock cambió mientras procesábamos tu solicitud. Por favor, intenta de nuevo.")
        }
        logger.info("[INVENTARIO] Stock actualizado. Servicio: {}, NuevoStock: {}", servicio.nombre, servicio.stock)
    }

    @Transactional
    fun devolverStock(servicio: ServicioMedico, cantidad: Int = 1) {
        if (servicio.stock == null) return

        logger.debug("[INVENTARIO] Devolviendo stock. Servicio: {}, Cantidad: {}, StockActual: {}", servicio.nombre, cantidad, servicio.stock)
        servicio.stock = servicio.stock!! + cantidad
        try {
            servicioMedicoRepository.save(servicio)
        } catch (ex: ObjectOptimisticLockingFailureException) {
            logger.warn("[INVENTARIO_CONCURRENCIA] Falló la devolución de stock para {}.", servicio.nombre)
            // En la devolución no lanzamos excepción al usuario, solo logueamos. Se puede reintentar internamente si es crítico.
        }
        logger.info("[INVENTARIO] Stock devuelto. Servicio: {}, NuevoStock: {}", servicio.nombre, servicio.stock)
    }
}

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

    @Transactional
    fun consumirStock(servicioId: UUID, cantidad: Int = 1, referencia: String) {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado: $servicioId") }

        logger.debug(
            "[INVENTARIO] Consumiendo stock e insumos. Servicio: {}, Cantidad: {}, Referencia: {}",
            servicio.nombre,
            cantidad,
            referencia
        )

        // 1. Consumir stock directo del servicio (ej: si es un producto físico)
        if (servicio.stock != null) {
            if (servicio.stock!! < cantidad) {
                throw ConflictException("No hay stock suficiente para ${servicio.nombre}")
            }
            servicio.stock = servicio.stock!! - cantidad
            servicioMedicoRepository.save(servicio)
        }

        // 2. Consumir insumos asociados con PESSIMISTIC LOCK
        servicio.insumos.forEach { si ->
            val totalRequerido = si.cantidadRequerida * cantidad

            // Bloqueamos la fila del insumo para actualización segura
            val insumo = insumoRepository.findByIdWithLock(si.insumo.id!!)
                ?: throw NotFoundException("Insumo no encontrado: ${si.insumo.nombre}")

            if (insumo.stockActual < totalRequerido) {
                if (si.critico) {
                    throw ConflictException("No hay stock suficiente del insumo crítico: ${insumo.nombre}")
                }
            } else {
                insumo.stockActual -= totalRequerido
                insumoRepository.save(insumo)
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

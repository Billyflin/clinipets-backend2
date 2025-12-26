package cl.clinipets.servicios.application

import cl.clinipets.agendamiento.domain.events.ConsultaFinalizadaEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class InventarioEventListener(
    private val inventarioService: InventarioService
) {
    private val logger = LoggerFactory.getLogger(InventarioEventListener::class.java)

    @EventListener
    fun onConsultaFinalizada(event: ConsultaFinalizadaEvent) {
        logger.info("[EVENT] Consulta Finalizada recibida: ${event.citaId}. Procesando inventario...")
        
        event.items.forEach { item ->
            try {
                inventarioService.consumirStock(
                    servicioId = item.servicioId,
                    cantidad = item.cantidad,
                    referencia = "Cita ${event.citaId}"
                )
            } catch (e: Exception) {
                logger.error("Error al consumir stock para servicio ${item.servicioId} en Cita ${event.citaId}: ${e.message}")
                // Rethrow to rollback transaction if this is synchronous
                throw e
            }
        }
        logger.info("[EVENT] Inventario actualizado correctamente para Cita ${event.citaId}")
    }
}

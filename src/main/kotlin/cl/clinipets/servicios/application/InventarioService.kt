package cl.clinipets.servicios.application

import cl.clinipets.core.web.ConflictException
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventarioService(
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    @Transactional
    fun consumirStock(servicio: ServicioMedico, cantidad: Int = 1) {
        if (servicio.stock == null) return

        if (servicio.stock!! < cantidad) {
            throw ConflictException("No hay stock suficiente para ${servicio.nombre}")
        }
        
        servicio.stock = servicio.stock!! - cantidad
        servicioMedicoRepository.save(servicio)
    }
}

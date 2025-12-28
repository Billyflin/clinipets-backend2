package cl.clinipets.servicios.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface ServicioInsumoRepository : JpaRepository<ServicioInsumo, UUID> {
    fun findByServicioId(servicioId: UUID): List<ServicioInsumo>
    fun findByInsumoId(insumoId: UUID): List<ServicioInsumo>
}

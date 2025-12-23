package cl.clinipets.servicios.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ServicioInsumoRepository : JpaRepository<ServicioInsumo, UUID> {
    fun findByServicioId(servicioId: UUID): List<ServicioInsumo>
}

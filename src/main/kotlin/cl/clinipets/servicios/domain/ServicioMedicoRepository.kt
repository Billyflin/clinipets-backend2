package cl.clinipets.servicios.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ServicioMedicoRepository : JpaRepository<ServicioMedico, UUID> {
    fun findByActivoTrue(): List<ServicioMedico>
    fun existsByNombreIgnoreCase(nombre: String): Boolean
}

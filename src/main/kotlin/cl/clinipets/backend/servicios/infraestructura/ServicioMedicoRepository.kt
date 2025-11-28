package cl.clinipets.backend.servicios.infraestructura

import cl.clinipets.backend.servicios.dominio.ServicioMedico
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServicioMedicoRepository : JpaRepository<ServicioMedico, Long> {
    fun findByNombre(nombre: String): ServicioMedico?
}

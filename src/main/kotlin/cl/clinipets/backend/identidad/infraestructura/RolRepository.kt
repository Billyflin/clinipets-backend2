package cl.clinipets.backend.identidad.infraestructura

import cl.clinipets.backend.identidad.dominio.Rol
import org.springframework.data.jpa.repository.JpaRepository

interface RolRepository : JpaRepository<Rol, Long> {
    fun findByNombre(nombre: String): Rol?
}


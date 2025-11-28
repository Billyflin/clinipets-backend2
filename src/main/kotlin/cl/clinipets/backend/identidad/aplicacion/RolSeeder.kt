package cl.clinipets.backend.identidad.aplicacion

import cl.clinipets.backend.identidad.dominio.Rol
import cl.clinipets.backend.identidad.dominio.Roles
import cl.clinipets.backend.identidad.infraestructura.RolRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class RolSeeder(private val repo: RolRepository) {

    @PostConstruct
    fun seed() {
        val base = listOf(Roles.ADMIN, Roles.CLIENTE, Roles.VETERINARIO)
        base.forEach { nombre ->
            if (repo.findByNombre(nombre) == null) {
                repo.save(Rol(nombre = nombre))
            }
        }
    }
}


package cl.clinipets.backend.identidad.infraestructura

import cl.clinipets.backend.identidad.dominio.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UsuarioRepository : JpaRepository<Usuario, UUID> {
    fun findByEmail(email: String): Usuario?
}


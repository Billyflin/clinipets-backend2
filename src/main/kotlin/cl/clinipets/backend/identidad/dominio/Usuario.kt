package cl.clinipets.backend.identidad.dominio

import cl.clinipets.backend.nucleo.api.AuditableEntity
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "usuarios")
class Usuario(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true)
    var email: String,

    var nombre: String? = null,
    var fotoUrl: String? = null,

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "usuarios_roles",
        joinColumns = [JoinColumn(name = "usuario_id")],
        inverseJoinColumns = [JoinColumn(name = "rol_id")]
    )
    var roles: MutableSet<Rol> = mutableSetOf()
) : AuditableEntity() {

    fun actualizarPerfil(nombre: String?, foto: String?) {
        this.nombre = nombre
        this.fotoUrl = foto
    }

    fun tieneRol(nombreRol: String): Boolean =
        roles.any { it.nombre.equals(nombreRol, ignoreCase = true) }

    fun asegurarRol(rol: Rol) {
        if (!tieneRol(rol.nombre)) {
            roles.add(rol)
        }
    }
}

@Entity
@Table(name = "roles")
class Rol(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var nombre: String
) : AuditableEntity()

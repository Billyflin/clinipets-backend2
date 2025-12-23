package cl.clinipets.servicios.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "insumos")
class Insumo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Version
    var version: Long? = null,

    @Column(nullable = false)
    var nombre: String,

    @Column(nullable = false)
    var stockActual: Double,

    @Column(nullable = false)
    var stockMinimo: Int,

    @Column(nullable = false)
    var unidadMedida: String
) : AuditableEntity() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Insumo) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Insumo(id=$id, nombre='$nombre')"
}

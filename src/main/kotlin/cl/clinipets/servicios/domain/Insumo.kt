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
    var stockMinimo: Double,

    @Column(nullable = false)
    var unidadMedida: String,

    @Column(length = 50)
    var contraindicacionMarcador: String? = null, // Ej: "ALERGIA_PENICILINA"

    @OneToMany(mappedBy = "insumo", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val lotes: MutableList<LoteInsumo> = mutableListOf()
) : AuditableEntity() {

    /**
     * Retorna el stock total sumando solo los lotes que no están vencidos.
     */
    fun stockVigente(): Double {
        return lotes.filter { !it.estaVencido() }.sumOf { it.cantidadActual }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Insumo) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Insumo(id=$id, nombre='$nombre')"
}

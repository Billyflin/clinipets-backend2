package cl.clinipets.servicios.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "servicio_insumos")
class ServicioInsumo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    val servicio: ServicioMedico,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id", nullable = false)
    val insumo: Insumo,

    @Column(nullable = false)
    val cantidadRequerida: Double,

    @Column(nullable = false)
    val critico: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServicioInsumo) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "ServicioInsumo(id=$id, cantidadRequerida=$cantidadRequerida)"
}

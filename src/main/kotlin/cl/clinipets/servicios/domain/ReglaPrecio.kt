package cl.clinipets.servicios.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "reglas_precio")
class ReglaPrecio(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val pesoMin: Double,

    @Column(nullable = false)
    val pesoMax: Double,

    @Column(nullable = false)
    val precio: BigDecimal,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    var servicio: ServicioMedico
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReglaPrecio) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "ReglaPrecio(id=$id, pesoMin=$pesoMin, pesoMax=$pesoMax, precio=$precio)"
}

package cl.clinipets.servicios.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "lotes_insumo")
class LoteInsumo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    val insumo: Insumo,

    @Column(nullable = false, length = 50)
    val codigoLote: String,

    @Column(nullable = false)
    val fechaVencimiento: LocalDate,

    @Column(nullable = false)
    val cantidadInicial: Double,

    @Column(nullable = false)
    var cantidadActual: Double
) : AuditableEntity() {

    fun estaVencido(): Boolean = fechaVencimiento.isBefore(LocalDate.now())

    fun tieneStock(): Boolean = cantidadActual > 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoteInsumo) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

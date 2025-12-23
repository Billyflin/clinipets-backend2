package cl.clinipets.veterinaria.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

enum class TipoPreventivo {
    VACUNA,
    DESPARASITACION_INTERNA,
    DESPARASITACION_EXTERNA
}

@Entity
@Table(name = "plan_preventivo")
class PlanPreventivo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = false)
    val mascota: Mascota,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val tipo: TipoPreventivo,

    @Column(nullable = false)
    val fechaAplicacion: Instant,

    @Column(nullable = false)
    val producto: String,

    @Column(nullable = true)
    val fechaRefuerzo: Instant? = null,

    @Column(nullable = true)
    val lote: String? = null,

    @Column(nullable = true)
    val observaciones: String? = null
) : AuditableEntity() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlanPreventivo) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "PlanPreventivo(id=$id, tipo=$tipo, producto='$producto')"
}

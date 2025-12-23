package cl.clinipets.veterinaria.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "signos_vitales")
class SignosVitales(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = false)
    val mascota: Mascota,

    @Column(nullable = false)
    var peso: Double,

    @Column(nullable = false)
    var temperatura: Double,

    @Column(nullable = false)
    var frecuenciaCardiaca: String,

    @Column(nullable = false)
    val fecha: Instant = Instant.now()
) : AuditableEntity() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignosVitales) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

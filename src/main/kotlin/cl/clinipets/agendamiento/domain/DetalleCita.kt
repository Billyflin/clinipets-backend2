package cl.clinipets.agendamiento.domain

import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Mascota
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "detalles_cita")
class DetalleCita(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cita_id", nullable = false)
    val cita: Cita,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    val servicio: ServicioMedico,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mascota_id", nullable = true)
    val mascota: Mascota?,

    @Column(nullable = false)
    var precioUnitario: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetalleCita) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

package cl.clinipets.agendamiento.domain

import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.veterinaria.domain.Mascota
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "detalles_cita")
data class DetalleCita(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cita_id", nullable = false)
    val cita: Cita,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    val servicio: ServicioMedico,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = true)
    val mascota: Mascota?,

    @Column(nullable = false)
    var precioUnitario: Int
)

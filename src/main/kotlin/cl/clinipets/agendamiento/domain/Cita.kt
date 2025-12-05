package cl.clinipets.agendamiento.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "citas")
data class Cita(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val fechaHoraInicio: Instant,

    @Column(nullable = false)
    val fechaHoraFin: Instant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var estado: EstadoCita,

    @Column(nullable = false)
    val precioFinal: Int,

    @OneToMany(mappedBy = "cita", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val detalles: MutableList<DetalleCita> = mutableListOf(),

    @Column(nullable = false)
    val tutorId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val origen: OrigenCita,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val tipoAtencion: TipoAtencion = TipoAtencion.CLINICA,

    @Column(length = 255)
    val direccion: String? = null,

    @Column(nullable = false)
    val montoAbono: Int = 0,

    @Column(length = 1024)
    var paymentUrl: String? = null,

    @Column(unique = true)
    var mpPaymentId: Long? = null,

    @Column(length = 50)
    var tokenCompensacion: String? = null
) : AuditableEntity()

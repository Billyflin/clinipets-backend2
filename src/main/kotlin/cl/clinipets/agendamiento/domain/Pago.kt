package cl.clinipets.agendamiento.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.identity.domain.User
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "pagos")
class Pago(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cita_id", nullable = false)
    val cita: Cita,

    @Column(nullable = false)
    val monto: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val metodo: MetodoPago,

    @Column(nullable = false)
    val fecha: Instant = Instant.now(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    val registradoPor: User,

    @Column(length = 255)
    val notas: String? = null
) : AuditableEntity()

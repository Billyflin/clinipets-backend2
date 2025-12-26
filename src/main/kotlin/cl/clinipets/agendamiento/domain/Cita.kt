package cl.clinipets.agendamiento.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.identity.domain.User
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class MetodoPago {
    EFECTIVO,
    TRANSFERENCIA,
    TARJETA_POS,
    MERCADO_PAGO_LINK
}

@Entity
@Table(
    name = "citas",
    indexes = [
        Index(name = "idx_cita_tutor", columnList = "tutor_id"),
        Index(name = "idx_cita_fecha_estado", columnList = "fechaHoraInicio, estado")
    ]
)
@SQLDelete(sql = "UPDATE citas SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
class Cita(
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
    var precioFinal: BigDecimal,

    @OneToMany(mappedBy = "cita", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val detalles: MutableList<DetalleCita> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    val tutor: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val origen: OrigenCita,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val tipoAtencion: TipoAtencion = TipoAtencion.CLINICA,

    @Column(length = 500)
    val motivoConsulta: String? = null,

    @Column(length = 255)
    val direccion: String? = null,

    @Column(length = 50)
    var tokenCompensacion: String? = null,

    @OneToMany(mappedBy = "cita", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val pagos: MutableList<Pago> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_finalizador_id")
    var staffFinalizador: User? = null
) : AuditableEntity() {

    fun totalPagado(): BigDecimal = pagos.fold(BigDecimal.ZERO) { acc, pago -> acc.add(pago.monto) }

    fun saldoPendiente(): BigDecimal = precioFinal.subtract(totalPagado()).max(BigDecimal.ZERO)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Cita) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Cita(id=$id, inicio=$fechaHoraInicio, estado=$estado)"

    /**
     * Cambia el estado de la cita validando que la transición sea permitida
     * @param nuevoEstado Estado al que se desea cambiar
     * @param responsable Email del usuario que realiza el cambio (para auditoría)
     */
    fun cambiarEstado(nuevoEstado: EstadoCita, responsable: String) {
        EstadoCitaTransiciones.validarTransicion(this.estado, nuevoEstado)
        val estadoAnterior = this.estado
        this.estado = nuevoEstado
        org.slf4j.LoggerFactory.getLogger(Cita::class.java)
            .info("[CITA] Estado cambiado: $estadoAnterior → $nuevoEstado por $responsable (Cita ID: $id)")
    }
}

package cl.clinipets.backend.agendamiento.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "citas")
data class Cita(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val fechaHoraInicio: LocalDateTime,

    @Column(nullable = false)
    val fechaHoraFin: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var estado: EstadoCita,

    @Column(nullable = false)
    val precioFinal: Int,

    @Column(nullable = false)
    val servicioId: UUID,

    @Column(nullable = false)
    val mascotaId: UUID,

    @Column(nullable = false)
    val tutorId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val origen: OrigenCita
) : AuditableEntity()

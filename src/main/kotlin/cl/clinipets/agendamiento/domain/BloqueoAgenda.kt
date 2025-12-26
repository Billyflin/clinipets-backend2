package cl.clinipets.agendamiento.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.identity.domain.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bloqueos_agenda")
data class BloqueoAgenda(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinario_id", nullable = false)
    val veterinario: User,

    @Column(nullable = false)
    val fechaHoraInicio: Instant,

    @Column(nullable = false)
    val fechaHoraFin: Instant,

    val motivo: String?
) : AuditableEntity()


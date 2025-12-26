package cl.clinipets.veterinaria.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "hitos_medicos")
class HitoMedico(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    val mascota: Mascota,

    @Column(nullable = false, length = 50)
    val marcador: String,

    @Column(length = 50)
    val valorAnterior: String?,

    @Column(nullable = false, length = 50)
    val valorNuevo: String,

    @Column(nullable = false)
    val fecha: Instant = Instant.now(),

    @Column(length = 255)
    val motivo: String? = null,

    val citaId: UUID? = null
) : AuditableEntity()

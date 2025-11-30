package cl.clinipets.backend.veterinaria.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.identity.domain.User
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "mascotas")
data class Mascota(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val nombre: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val especie: Especie,

    @Column(nullable = false, precision = 10, scale = 2)
    val pesoActual: BigDecimal,

    @Column(nullable = false)
    val fechaNacimiento: LocalDate,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    val tutor: User
) : AuditableEntity()

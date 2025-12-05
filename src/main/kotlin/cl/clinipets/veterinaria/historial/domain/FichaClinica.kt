package cl.clinipets.veterinaria.historial.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.veterinaria.domain.Mascota
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "fichas_clinicas")
data class FichaClinica(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    val mascota: Mascota,

    @Column(nullable = false)
    val fechaAtencion: Instant = Instant.now(),

    @Column(nullable = false)
    val motivoConsulta: String,

    @Column(columnDefinition = "TEXT")
    val anamnesis: String? = null,

    @Column(columnDefinition = "TEXT")
    val examenFisico: String? = null,

    @Column(columnDefinition = "TEXT")
    val tratamiento: String? = null,

    @Column(nullable = true)
    val pesoRegistrado: Double? = null,

    @Column(columnDefinition = "TEXT")
    val observaciones: String? = null,

    @Column(columnDefinition = "TEXT")
    val diagnostico: String? = null,

    @Column(nullable = false)
    val esVacuna: Boolean = false,

    val nombreVacuna: String? = null,

    val fechaProximaVacuna: LocalDate? = null,
    val fechaProximoControl: LocalDate? = null,
    val fechaDesparasitacion: LocalDate? = null,

    val fechaProximaDosis: LocalDate? = null, // Deprecated in favor of fechaProximaVacuna

    @Column(nullable = false)
    val autorId: UUID
) : AuditableEntity()

package cl.clinipets.veterinaria.historial.domain

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.identity.domain.User
import cl.clinipets.veterinaria.domain.Mascota
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import org.hibernate.envers.RelationTargetAuditMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Embeddable
data class SignosVitalesData(
    val pesoRegistrado: Double? = null,
    val temperatura: Double? = null,
    val frecuenciaCardiaca: Int? = null,
    val frecuenciaRespiratoria: Int? = null,

    @Column(nullable = false, columnDefinition = "boolean default false")
    val alertaVeterinaria: Boolean = false
)

@Embeddable
data class PlanSanitario(
    @Column(nullable = false)
    val esVacuna: Boolean = false,
    val nombreVacuna: String? = null,
    val fechaProximaVacuna: LocalDate? = null,
    val fechaProximoControl: LocalDate? = null,
    val fechaDesparasitacion: LocalDate? = null
)

@Entity
@Audited
@Table(name = "fichas_clinicas")
@SQLDelete(sql = "UPDATE fichas_clinicas SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
data class FichaClinica(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    val mascota: Mascota,

    @NotAudited
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id", nullable = true)
    val cita: Cita? = null,

    @Column(nullable = false)
    val fechaAtencion: Instant = Instant.now(),

    @Column(nullable = false)
    val motivoConsulta: String,

    @Column(columnDefinition = "TEXT")
    val anamnesis: String? = null, // S: Subjetivo

    @Column(columnDefinition = "TEXT")
    val hallazgosObjetivos: String? = null, // O: Objetivo

    @Column(columnDefinition = "TEXT")
    val avaluoClinico: String? = null, // A: Avalúo

    @Column(columnDefinition = "TEXT")
    val planTratamiento: String? = null, // P: Plan

    @Embedded
    val signosVitales: SignosVitalesData = SignosVitalesData(),

    @Column(columnDefinition = "TEXT")
    val observaciones: String? = null,

    @Embedded
    val planSanitario: PlanSanitario = PlanSanitario(),

    @NotAudited
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    val autor: User
) : AuditableEntity()
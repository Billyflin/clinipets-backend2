package cl.clinipets.veterinaria.historial.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.servicios.domain.Insumo
import jakarta.persistence.*
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import java.util.UUID

@Entity
@Audited
@Table(name = "recetas_medicas")
class RecetaMedica(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_id", nullable = false)
    val ficha: FichaClinica,

    @OneToMany(mappedBy = "receta", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<ItemPrescripcion> = mutableListOf(),

    @Column(columnDefinition = "TEXT")
    var observaciones: String? = null
) : AuditableEntity()

@Entity
@Audited
@Table(name = "items_prescripcion")
class ItemPrescripcion(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id", nullable = false)
    val receta: RecetaMedica,

    @NotAudited
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    val insumo: Insumo,

    @Column(nullable = false)
    val dosis: String,

    @Column(nullable = false)
    val frecuencia: String,

    @Column(nullable = false)
    val duracion: String,

    @Column(nullable = false)
    val cantidadADespachar: Double
) : AuditableEntity()

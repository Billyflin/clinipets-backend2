package cl.clinipets.servicios.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class TipoDescuento {
    PRECIO_FIJO,
    MONTO_OFF,
    PORCENTAJE_OFF
}

@Embeddable
data class PromocionBeneficio(
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "servicio_id", nullable = false)
    val servicio: ServicioMedico,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val tipo: TipoDescuento,

    @Column(nullable = false)
    val valor: BigDecimal
)

@Entity
@Table(name = "promociones")
data class Promocion(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val nombre: String,

    @Column(length = 500)
    val descripcion: String? = null,

    @Column(nullable = false)
    val fechaInicio: LocalDate,

    @Column(nullable = false)
    val fechaFin: LocalDate,

    @Column(name = "dias_permitidos", length = 50)
    val diasPermitidos: String? = null, // Formato "MON,TUE"

    @Column(nullable = false)
    val activa: Boolean = true,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "promocion_triggers",
        joinColumns = [JoinColumn(name = "promocion_id")],
        inverseJoinColumns = [JoinColumn(name = "servicio_id")]
    )
    val serviciosTrigger: MutableSet<ServicioMedico> = mutableSetOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promocion_beneficios", joinColumns = [JoinColumn(name = "promocion_id")])
    val beneficios: MutableList<PromocionBeneficio> = mutableListOf()

) : AuditableEntity() {

    fun estaVigente(fechaCita: LocalDate): Boolean {
        if (!activa) return false
        if (fechaCita.isBefore(fechaInicio) || fechaCita.isAfter(fechaFin)) return false

        val dias = diasPermitidos
        return if (dias.isNullOrBlank()) {
            true
        } else {
            val diaSemanaIngles = fechaCita.dayOfWeek.name.take(3).uppercase() // MON, TUE...
            dias.uppercase().contains(diaSemanaIngles)
        }
    }
}

@Repository
interface PromocionRepository : JpaRepository<Promocion, UUID> {
    fun findAllByActivaTrue(): List<Promocion>
}
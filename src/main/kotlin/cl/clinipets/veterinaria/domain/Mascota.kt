package cl.clinipets.veterinaria.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.identity.domain.User
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "mascotas",
    indexes = [
        Index(name = "idx_mascota_tutor", columnList = "tutor_id"),
        Index(name = "idx_mascota_chip", columnList = "chipIdentificador")
    ]
)
@SQLDelete(sql = "UPDATE mascotas SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
data class Mascota(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    var nombre: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val especie: Especie,

    @Column(nullable = false)
    var pesoActual: Double,

    @Column(nullable = false)
    var raza: String = "Mestizo",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var sexo: Sexo,

    @Column(nullable = false)
    var esterilizado: Boolean = false,

    @Column(unique = true)
    var chipIdentificador: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var temperamento: Temperamento = Temperamento.DOCIL,

    @Column(nullable = false)
    val fechaNacimiento: LocalDate,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    var tutor: User,

    @OneToMany(mappedBy = "mascota", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val signosVitales: MutableList<SignosVitales> = mutableListOf(),

    @Column(nullable = false)
    var testRetroviralNegativo: Boolean = false,

    @Column(nullable = true)
    var fechaUltimoTestRetroviral: LocalDate? = null,

    @Column(nullable = true, length = 2000)
    var observacionesClinicas: String? = null
) : AuditableEntity()
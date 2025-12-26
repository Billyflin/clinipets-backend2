package cl.clinipets.veterinaria.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.identity.domain.User
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Audited
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

    @Column(nullable = true)
    var pesoActual: Double? = null,

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

    @Column(nullable = true)
    val fechaNacimiento: LocalDate?,

    @NotAudited
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    var tutor: User,

    @NotAudited
    @OneToMany(mappedBy = "mascota", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val signosVitales: MutableList<SignosVitales> = mutableListOf(),

    @Column(nullable = false)
    var testRetroviralNegativo: Boolean = false,

    @Column(nullable = true)
    var fechaUltimoTestRetroviral: LocalDate? = null,

        @Column(nullable = true, length = 2000)

        var observacionesClinicas: String? = null,

    

        @NotAudited

        @ElementCollection(fetch = FetchType.EAGER)

        @CollectionTable(name = "mascota_marcadores", joinColumns = [JoinColumn(name = "mascota_id")])

        @MapKeyColumn(name = "clave")

        @Column(name = "valor")

        var marcadores: MutableMap<String, String> = mutableMapOf()

    ) : AuditableEntity()

    
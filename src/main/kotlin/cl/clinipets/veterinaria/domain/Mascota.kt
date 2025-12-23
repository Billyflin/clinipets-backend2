package cl.clinipets.veterinaria.domain

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

    @Column(nullable = false)
    var testRetroviralNegativo: Boolean = false,

    @Column(nullable = true)
    var fechaUltimoTestRetroviral: LocalDate? = null,

    @Column(nullable = true, length = 2000)
    var observacionesClinicas: String? = null
) : AuditableEntity()
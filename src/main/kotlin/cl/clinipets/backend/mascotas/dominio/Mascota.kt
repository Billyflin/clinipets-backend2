package cl.clinipets.backend.mascotas.dominio

import cl.clinipets.backend.identidad.dominio.Usuario
import cl.clinipets.backend.nucleo.api.AuditableEntity
import com.fasterxml.jackson.annotation.JsonBackReference
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDate
import java.util.*

@NamedEntityGraph(
    name = "Mascota.withRazaAndColores", // Nombre de nuestro plan de carga
    attributeNodes = [
        NamedAttributeNode("raza"),    // Cargar la relación 'raza'
        NamedAttributeNode("colores")  // Cargar la colección 'colores'
    ]
)
@Entity
@Table(
    name = "mascotas",
    indexes = [Index(name = "idx_mascotas_tutor_id", columnList = "tutor_id")]
)
class Mascota(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var nombre: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var especie: Especie,

    // Esta relación es LAZY y causaba el error
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "raza_id", nullable = true, foreignKey = ForeignKey(name = "fk_mascota_raza"))
    var raza: Raza? = null,

    @Enumerated(EnumType.STRING)
    var sexo: Sexo? = null,

    var fechaNacimiento: LocalDate? = null,

    @Column(nullable = false)
    var esFechaAproximada: Boolean = false,

    var pesoKg: Double? = null,

    @Enumerated(EnumType.STRING)
    var pelaje: Pelaje? = null,

    @Enumerated(EnumType.STRING)
    var patron: Patron? = null,

    @Enumerated(EnumType.STRING)
    var colores: MutableSet<Color> = mutableSetOf(),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "tutor_id",
        nullable = false,
        columnDefinition = "uuid",
        foreignKey = ForeignKey(name = "fk_mascota_tutor")
    )
    @JsonBackReference("usuario-mascotas")
    var tutor: Usuario
) : AuditableEntity()

enum class Especie { PERRO, GATO }
enum class Sexo { MACHO, HEMBRA }
enum class Pelaje { CORTO, MEDIO, LARGO, RIZADO, SIN_PELO }
enum class Patron {
    SOLIDO,
    BICOLOR,
    TRICOLOR,
    TABBY,
    MANCHAS_MERLE,
    ATIGRADO_BRINDLE
}

enum class Color {
    NEGRO,
    BLANCO,
    GRIS,
    NARANJA_ROJIZO,
    CREMA,
    CAFE_CHOCOLATE,
    AZUL,
    DORADO,
    FUEGO_TAN
}
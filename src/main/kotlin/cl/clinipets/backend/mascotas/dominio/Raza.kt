package cl.clinipets.backend.mascotas.dominio

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "razas")
class Raza(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var nombre: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var especie: Especie
)
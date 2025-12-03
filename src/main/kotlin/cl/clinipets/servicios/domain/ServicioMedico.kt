package cl.clinipets.servicios.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "servicios_medicos")
data class ServicioMedico(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val nombre: String,

    @Column(nullable = false)
    val precioBase: Int,

    @Column(nullable = false)
    val requierePeso: Boolean,

    @Column(nullable = false)
    val duracionMinutos: Int,

    @Column(nullable = false)
    val activo: Boolean = true,

    @OneToMany(mappedBy = "servicio", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val reglas: MutableList<ReglaPrecio> = mutableListOf()
) : AuditableEntity()

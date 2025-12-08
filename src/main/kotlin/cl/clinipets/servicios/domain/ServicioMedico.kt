package cl.clinipets.servicios.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "servicios_medicos")
data class ServicioMedico(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Version
    var version: Long? = null,

    @Column(nullable = false, unique = true)
    var nombre: String,

    @Column(nullable = false)
    var precioBase: Int,

    @Column(nullable = true)
    var precioAbono: Int? = null,

    @Column(nullable = false)
    var requierePeso: Boolean,

    @Column(nullable = false)
    var duracionMinutos: Int,

    @Column(nullable = false)
    var activo: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var categoria: CategoriaServicio = CategoriaServicio.OTRO,

    @ElementCollection(targetClass = Especie::class, fetch = FetchType.EAGER)
    @CollectionTable(name = "servicio_especies", joinColumns = [JoinColumn(name = "servicio_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "especie", nullable = false)
    var especiesPermitidas: MutableSet<Especie> = mutableSetOf(),

    @Column(nullable = true)
    var stock: Int? = null,

    @Column(nullable = false)
    var bloqueadoSiEsterilizado: Boolean = false,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "servicio_dependencias", joinColumns = [JoinColumn(name = "servicio_id")])
    @Column(name = "servicio_requerido_id")
    var serviciosRequeridosIds: MutableSet<UUID> = mutableSetOf(),

    @OneToMany(mappedBy = "servicio", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val reglas: MutableList<ReglaPrecio> = mutableListOf()
) : AuditableEntity() {
    fun calcularPrecioPara(mascota: Mascota): Int {
        if (!requierePeso) return precioBase
        val regla = reglas.firstOrNull {
            (mascota.pesoActual >= it.pesoMin) && (mascota.pesoActual <= it.pesoMax)
        }
        return regla?.precio ?: precioBase
    }
}

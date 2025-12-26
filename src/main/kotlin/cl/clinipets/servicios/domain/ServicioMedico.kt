package cl.clinipets.servicios.domain

import cl.clinipets.core.domain.AuditableEntity
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "servicios_medicos")
class ServicioMedico(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Version
    var version: Long? = null,

    @Column(nullable = false)
    var nombre: String,

    @Column(nullable = false)
    var precioBase: BigDecimal,

    @Column(nullable = true)
    var precioAbono: BigDecimal? = null,

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "servicio_dependencias_rel",
        joinColumns = [JoinColumn(name = "servicio_id")],
        inverseJoinColumns = [JoinColumn(name = "servicio_requerido_id")]
    )
    var serviciosRequeridos: MutableSet<ServicioMedico> = mutableSetOf(),

    @OneToMany(mappedBy = "servicio", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    val reglas: MutableSet<ReglaPrecio> = mutableSetOf(),

    @OneToMany(mappedBy = "servicio", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    val insumos: MutableSet<ServicioInsumo> = mutableSetOf(),

    @Column(length = 50)
    var actualizaMarcador: String? = null,

    @Column(length = 50)
    var condicionMarcadorClave: String? = null,

    @Column(length = 50)
    var condicionMarcadorValor: String? = null
) : AuditableEntity() {
    fun calcularPrecioPara(mascota: Mascota): BigDecimal {
        if (!requierePeso) return precioBase
        val peso = mascota.pesoActual ?: return precioBase
        val regla = reglas.firstOrNull {
            (peso >= it.pesoMin) && (peso <= it.pesoMax)
        }
        return regla?.precio ?: precioBase
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServicioMedico) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "ServicioMedico(id=$id, nombre='$nombre')"
}

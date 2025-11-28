package cl.clinipets.backend.servicios.dominio

import cl.clinipets.backend.nucleo.api.AuditableEntity
import com.fasterxml.jackson.annotation.JsonManagedReference
import jakarta.persistence.*

@Entity
@Table(name = "servicios_medicos")
class ServicioMedico(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var nombre: String,

    @Column(name = "precio_base", nullable = false)
    var precioBase: Int = 0,

    @Column(name = "requiere_peso", nullable = false)
    var requierePeso: Boolean = false,

    @Column(name = "es_urgencia", nullable = false)
    var esUrgencia: Boolean = false,

    @Column(name = "duracion_minutos", nullable = false)
    var duracionMinutos: Int = 30,

    var descripcion: String? = null,

    @OneToMany(mappedBy = "servicio", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    var reglasPrecio: MutableList<ReglaPrecio> = mutableListOf()
) : AuditableEntity() {

    fun agregarRegla(regla: ReglaPrecio) {
        reglasPrecio.add(regla)
        regla.servicio = this
    }
}

package cl.clinipets.backend.servicios.dominio

import cl.clinipets.backend.nucleo.api.AuditableEntity
import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "reglas_precios")
class ReglaPrecio(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "peso_min", nullable = false, precision = 5, scale = 2)
    var pesoMin: BigDecimal,

    @Column(name = "peso_max", nullable = false, precision = 5, scale = 2)
    var pesoMax: BigDecimal,

    @Column(nullable = false)
    var precio: Int,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    @JsonBackReference
    var servicio: ServicioMedico? = null
) : AuditableEntity()

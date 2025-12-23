package cl.clinipets.servicios.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "reglas_precio")
data class ReglaPrecio(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val pesoMin: Double,

    @Column(nullable = false)
    val pesoMax: Double,

    @Column(nullable = false)
    val precio: Int,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    var servicio: ServicioMedico
)

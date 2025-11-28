package cl.clinipets.backend.agendamiento.dominio

import cl.clinipets.backend.mascotas.dominio.Mascota
import cl.clinipets.backend.nucleo.api.AuditableEntity
import cl.clinipets.backend.servicios.dominio.ServicioMedico
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "citas")
class Cita(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "fecha_hora", nullable = false)
    var fechaHora: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var estado: EstadoCita = EstadoCita.PENDIENTE_PAGO,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = false)
    var mascota: Mascota,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    var servicioMedico: ServicioMedico,

    @Column(name = "precio_final", nullable = false)
    var precioFinal: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var origen: OrigenCita
) : AuditableEntity()

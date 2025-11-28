package cl.clinipets.backend.veterinaria.dominio

import cl.clinipets.backend.mascotas.dominio.Mascota
import cl.clinipets.backend.nucleo.api.AuditableEntity
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "eventos_medicos")
class EventoMedico(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = false)
    val mascota: Mascota,

    @Column(nullable = false)
    val fecha: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val tipo: TipoEvento,

    @Column(columnDefinition = "TEXT")
    val descripcion: String,

    @Column(name = "fecha_proximo_evento")
    val fechaProximoEvento: LocalDate? = null,

    @Column(name = "usuario_responsable")
    val usuarioResponsable: String? = null

) : AuditableEntity()

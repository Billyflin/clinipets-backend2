package cl.clinipets.veterinaria.galeria.domain

import cl.clinipets.veterinaria.domain.Mascota
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mascota_media")
class MascotaMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = false)
    var mascota: Mascota,

    @Column(nullable = false, length = 1024)
    var url: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var tipo: MediaType,

    @Column(length = 255)
    var titulo: String? = null,

    @Column(name = "fecha_subida", nullable = false)
    var fechaSubida: Instant = Instant.now()
)

enum class MediaType {
    IMAGE,
    PDF,
    LAB_RESULT,
    OTHER
}

package cl.clinipets.backend.nucleo.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.*


@Schema(
    description = "Entidad base que proporciona campos de auditoría para el seguimiento de creación y modificación de registros.",
    accessMode = Schema.AccessMode.READ_ONLY
)
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    @field:Schema(
        description = "Fecha de creación del registro",
        example = "2025-11-14T12:34:56Z",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    @field:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    var creadoEn: Instant = Instant.now()

    @LastModifiedDate
    @Column(name = "modificado_en", nullable = false)
    @field:Schema(
        description = "Fecha de última modificación del registro",
        example = "2025-11-14T12:34:56Z",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    @field:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    var modificadoEn: Instant = Instant.now()

    @CreatedBy
    @Column(name = "creado_por", columnDefinition = "uuid", updatable = false)
    @field:Schema(
        description = "Identificador del usuario que creó el registro",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    @field:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    var creadoPor: UUID? = null

    @LastModifiedBy
    @Column(name = "modificado_por", columnDefinition = "uuid")
    @field:Schema(
        description = "Identificador del usuario que modificó el registro",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    @field:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    var modificadoPor: UUID? = null
}

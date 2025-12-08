package cl.clinipets.veterinaria.galeria.api

import cl.clinipets.veterinaria.galeria.domain.MediaType
import java.time.Instant
import java.util.UUID

data class MediaResponse(
    val id: UUID,
    val url: String,
    val tipo: MediaType,
    val titulo: String?,
    val fechaSubida: Instant
)

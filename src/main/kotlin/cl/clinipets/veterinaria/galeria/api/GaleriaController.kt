package cl.clinipets.veterinaria.galeria.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.veterinaria.galeria.application.GaleriaService
import cl.clinipets.veterinaria.galeria.domain.MediaType
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.MediaType as HttpMediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders

@RestController
@RequestMapping("/api/v1/mascotas/{mascotaId}/galeria")
class GaleriaController(
    private val galeriaService: GaleriaService
) {

    @Operation(summary = "Subir archivo a la galería de la mascota")
    @PostMapping("/upload", consumes = [HttpMediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadFile(
        @PathVariable mascotaId: UUID,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("titulo", required = false) titulo: String?,
        @RequestParam("tipo", defaultValue = "IMAGE") tipo: MediaType,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MediaResponse> {
        val response = galeriaService.subirArchivo(mascotaId, file, titulo, tipo, principal)

        // Transformar internal path a public URL para la respuesta inmediata
        val publicUrl = "/api/v1/mascotas/$mascotaId/galeria/ver/${response.id}"

        return ResponseEntity.ok(response.copy(url = publicUrl))
    }

    @Operation(summary = "Listar galería de la mascota")
    @GetMapping
    fun listarGaleria(
        @PathVariable mascotaId: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<MediaResponse>> {
        val internalList = galeriaService.listarArchivos(mascotaId, principal)

        // Transformar path interno a URL pública
        val publicList = internalList.map {
            it.copy(url = "/api/v1/mascotas/$mascotaId/galeria/ver/${it.id}")
        }

        return ResponseEntity.ok(publicList)
    }

    @Operation(summary = "Descargar/Visualizar archivo de la galería")
    @GetMapping("/ver/{mediaId}")
    fun verArchivo(
        @PathVariable mascotaId: UUID,
        @PathVariable mediaId: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<InputStreamResource> {
        val (inputStream, contentType) = galeriaService.descargarArchivo(mediaId, principal)

        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_TYPE, contentType)
        // Opcional: headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"...\"")

        return ResponseEntity.ok()
            .headers(headers)
            .body(InputStreamResource(inputStream))
    }
}

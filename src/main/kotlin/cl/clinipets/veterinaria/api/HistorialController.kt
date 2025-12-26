package cl.clinipets.veterinaria.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.veterinaria.historial.application.HistorialClinicoService
import cl.clinipets.veterinaria.historial.application.HistorialCompletoResponse
import cl.clinipets.veterinaria.historial.application.HistorialEvolucionDto
import cl.clinipets.veterinaria.historial.application.PdfService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController @RequestMapping("/api/v1/historial")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Historial Clínico", description = "Historial médico completo de mascotas")
class HistorialController(
    private val historialService: HistorialClinicoService,
    private val pdfService: PdfService
) {

    @Operation(summary = "Obtener historial clínico completo de una mascota")
    @GetMapping("/mascota/{mascotaId}")
    fun obtenerHistorialCompleto(
        @PathVariable mascotaId: UUID,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<HistorialCompletoResponse> {
        val historial = historialService.obtenerHistorialCompleto(mascotaId, user)
        return ResponseEntity.ok(historial)
    }

    @Operation(summary = "Obtener evolución de signos vitales (peso y temperatura) histórica")
    @GetMapping("/mascota/{mascotaId}/evolucion")
    fun obtenerEvolucion(
        @PathVariable mascotaId: UUID,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<HistorialEvolucionDto> {
        return ResponseEntity.ok(historialService.obtenerEvolucionMedica(mascotaId, user))
    }

    @Operation(summary = "Descargar carnet sanitario en PDF")
    @GetMapping("/mascota/{mascotaId}/carnet-pdf")
    fun descargarCarnetPdf(
        @PathVariable mascotaId: UUID,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<ByteArrayResource> {
        val pdfBytes = pdfService.generarCarnetSanitarioPdf(mascotaId, user)
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_PDF
            add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"carnet-$mascotaId.pdf\"")
        }
        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(pdfBytes.size.toLong())
            .body(ByteArrayResource(pdfBytes))
    }
}

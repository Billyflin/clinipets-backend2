package cl.clinipets.veterinaria.historial.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.application.FichaClinicaService
import cl.clinipets.veterinaria.historial.application.PdfService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/fichas")
class FichaClinicaController(
    private val fichaService: FichaClinicaService,
    private val mascotaRepository: MascotaRepository, // Needed for ownership check
    private val pdfService: PdfService
) {
    private val logger = LoggerFactory.getLogger(FichaClinicaController::class.java)

    @Operation(summary = "Crear ficha clínica (Admin/Staff)", operationId = "crearFicha")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Ficha creada exitosamente"),
        ApiResponse(responseCode = "400", description = "Datos inválidos"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    fun crearFicha(
        @Valid @RequestBody request: FichaCreateRequest,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<FichaResponse> {
        logger.info("[CREAR_FICHA] Inicio. Staff: {}, MascotaID: {}", user.email, request.mascotaId)
        val ficha = fichaService.crearFicha(request, user.userId)
        logger.info("[CREAR_FICHA] Fin - Exitoso. FichaID: {}", ficha.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(ficha)
    }

    @Operation(summary = "Obtener historial médico paginado", operationId = "obtenerHistorial")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Historial obtenido exitosamente"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada"),
        ApiResponse(responseCode = "403", description = "No tiene permiso para ver esta mascota")
    )
    @GetMapping("/mascota/{mascotaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CLIENT')")
    fun obtenerHistorial(
        @PathVariable mascotaId: UUID,
        @PageableDefault(size = 10, sort = ["fechaAtencion"], direction = Sort.Direction.DESC) pageable: Pageable,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<Page<FichaResponse>> {
        logger.info("[HISTORIAL] Inicio. User: {}, MascotaID: {}, Page: {}", user.email, mascotaId, pageable.pageNumber)
        
        // If user is CLIENT, must verify ownership
        if (user.role == UserRole.CLIENT) {
             val mascota = mascotaRepository.findById(mascotaId).orElse(null)
             // If mascota doesn't exist, Service will throw NotFound, but here we check access
             if (mascota != null && mascota.tutor.id != user.userId) {
                 logger.warn("[HISTORIAL] Acceso denegado. Mascota {} no pertenece a {}", mascotaId, user.email)
                 throw UnauthorizedException("No tiene permiso para ver el historial de esta mascota.")
             }
        }

        val historial = fichaService.obtenerHistorial(mascotaId, pageable)
        logger.info("[HISTORIAL] Fin - Registros: {}", historial.numberOfElements)
        return ResponseEntity.ok(historial)
    }

    @Operation(summary = "Descargar PDF de ficha clínica", operationId = "descargarFichaPdf")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "PDF generado exitosamente"),
        ApiResponse(responseCode = "404", description = "Ficha no encontrada")
    )
    @GetMapping("/{fichaId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CLIENT')")
    fun descargarFichaPdf(
        @PathVariable fichaId: UUID,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<ByteArrayResource> {
        logger.info("[FICHA_PDF] Solicitud de PDF. Ficha: {}, Usuario: {}", fichaId, user.email)
        val pdfBytes = pdfService.generarFichaClinicaPdf(fichaId, user)
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_PDF
            add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ficha-$fichaId.pdf\"")
        }
        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(pdfBytes.size.toLong())
            .body(ByteArrayResource(pdfBytes))
    }
}

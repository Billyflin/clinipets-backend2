package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.application.BloqueoService
import cl.clinipets.agendamiento.domain.BloqueoAgenda
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class BloqueoCreateRequest(
    val fechaHoraInicio: Instant,
    val fechaHoraFin: Instant,
    val motivo: String?
)

@RestController
@RequestMapping("/api/v1/bloqueos")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
class BloqueoController(private val bloqueoService: BloqueoService) {
    private val logger = LoggerFactory.getLogger(BloqueoController::class.java)

    @Operation(summary = "Crear un bloqueo de agenda", operationId = "crearBloqueo")
    @PostMapping
    fun crear(
        @Valid @RequestBody request: BloqueoCreateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<BloqueoAgenda> {
        logger.info("[CREAR_BLOQUEO] Request. User: {}, Inicio: {}", principal.email, request.fechaHoraInicio)
        val bloqueo = bloqueoService.crearBloqueo(
            principal.userId,
            request.fechaHoraInicio,
            request.fechaHoraFin,
            request.motivo
        )
        logger.info("[CREAR_BLOQUEO] Fin request - Exitoso")
        return ResponseEntity.ok(bloqueo)
    }

    @Operation(summary = "Eliminar un bloqueo de agenda", operationId = "eliminarBloqueo")
    @DeleteMapping("/{id}")
    fun eliminar(@PathVariable id: UUID): ResponseEntity<Void> {
        logger.info("[ELIMINAR_BLOQUEO] Request. ID: {}", id)
        bloqueoService.eliminarBloqueo(id)
        logger.info("[ELIMINAR_BLOQUEO] Fin request - Exitoso")
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Listar bloqueos por fecha", operationId = "listarBloqueos")
    @GetMapping
    fun listar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate): ResponseEntity<List<BloqueoAgenda>> {
        logger.info("[LISTAR_BLOQUEOS] Request. Fecha: {}", fecha)
        val bloqueos = bloqueoService.obtenerBloqueos(fecha)
        logger.info("[LISTAR_BLOQUEOS] Fin request - Encontrados: {}", bloqueos.size)
        return ResponseEntity.ok(bloqueos)
    }
}


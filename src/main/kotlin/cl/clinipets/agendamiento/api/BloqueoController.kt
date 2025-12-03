package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.application.BloqueoService
import cl.clinipets.agendamiento.domain.BloqueoAgenda
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
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

    @Operation(summary = "Crear un bloqueo de agenda", operationId = "crearBloqueo")
    @PostMapping
    fun crear(
        @Valid @RequestBody request: BloqueoCreateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<BloqueoAgenda> {
        val bloqueo = bloqueoService.crearBloqueo(
            principal.userId,
            request.fechaHoraInicio,
            request.fechaHoraFin,
            request.motivo
        )
        return ResponseEntity.ok(bloqueo)
    }

    @Operation(summary = "Eliminar un bloqueo de agenda", operationId = "eliminarBloqueo")
    @DeleteMapping("/{id}")
    fun eliminar(@PathVariable id: UUID): ResponseEntity<Void> {
        bloqueoService.eliminarBloqueo(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Listar bloqueos por fecha", operationId = "listarBloqueos")
    @GetMapping
    fun listar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate): ResponseEntity<List<BloqueoAgenda>> {
        return ResponseEntity.ok(bloqueoService.obtenerBloqueos(fecha))
    }
}


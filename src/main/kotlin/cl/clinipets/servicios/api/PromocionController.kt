package cl.clinipets.servicios.api

import cl.clinipets.servicios.application.PromocionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/promociones")
@Tag(name = "Promociones", description = "Endpoints para la gestión de promociones y descuentos")
class PromocionController(
    private val promocionService: PromocionService
) {

    @Operation(summary = "Listar todas las promociones (Staff/Admin)", operationId = "listarPromociones")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping
    fun listar(): ResponseEntity<List<PromocionResponse>> {
        return ResponseEntity.ok(promocionService.listarTodas())
    }

    @Operation(summary = "Crear nueva promoción (Staff/Admin)", operationId = "crearPromocion")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping
    fun crear(@Valid @RequestBody request: PromocionCreateRequest): ResponseEntity<PromocionResponse> {
        return ResponseEntity.ok(promocionService.crear(request))
    }

    @Operation(summary = "Actualizar promoción (Staff/Admin)", operationId = "actualizarPromocion")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PromocionUpdateRequest
    ): ResponseEntity<PromocionResponse> {
        return ResponseEntity.ok(promocionService.actualizar(id, request))
    }

    @Operation(summary = "Eliminar promoción (Staff/Admin)", operationId = "eliminarPromocion")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @DeleteMapping("/{id}")
    fun eliminar(@PathVariable id: UUID): ResponseEntity<Unit> {
        promocionService.eliminar(id)
        return ResponseEntity.noContent().build()
    }
}

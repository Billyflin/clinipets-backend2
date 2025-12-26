package cl.clinipets.veterinaria.api

import cl.clinipets.veterinaria.historial.application.AuditService
import cl.clinipets.veterinaria.historial.application.FichaRevisionDto
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(private val auditService: AuditService) {

    @Operation(summary = "Obtener historial de cambios de una ficha clínica (Staff/Admin)")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/ficha/{id}")
    fun getFichaHistory(@PathVariable id: UUID): ResponseEntity<List<FichaRevisionDto>> {
        val history = auditService.obtenerRevisionesFicha(id)
        return ResponseEntity.ok(history)
    }
}

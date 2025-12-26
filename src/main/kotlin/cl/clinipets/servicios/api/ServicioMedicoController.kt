package cl.clinipets.servicios.api

import cl.clinipets.servicios.application.ServicioMedicoService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import java.util.UUID

@RestController
@RequestMapping("/api/v1/servicios")
class ServicioMedicoController(
    private val servicioMedicoService: ServicioMedicoService
) {
    private val logger = LoggerFactory.getLogger(ServicioMedicoController::class.java)

    @Operation(summary = "Listar servicios activos", operationId = "listarServicios")
    @GetMapping
    fun listar(): ResponseEntity<List<ServicioMedicoDto>> {
        logger.info("[LISTAR_SERVICIOS] Inicio request")
        val response = servicioMedicoService.listarActivos()
        logger.info("[LISTAR_SERVICIOS] Fin request - Encontrados: {}", response.size)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Actualizar dependencias de un servicio (Staff/Admin)", operationId = "actualizarDependencias")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PutMapping("/{id}/dependencias")
    fun actualizarDependencias(
        @PathVariable id: UUID,
        @RequestBody nuevosIdsRequeridos: Set<UUID>
    ): ResponseEntity<ServicioMedicoDto> {
        logger.info("[ACTUALIZAR_DEPENDENCIAS] Request para Servicio ID: {}", id)
        val response = servicioMedicoService.actualizarDependencias(id, nuevosIdsRequeridos)
        return ResponseEntity.ok(response)
    }
}

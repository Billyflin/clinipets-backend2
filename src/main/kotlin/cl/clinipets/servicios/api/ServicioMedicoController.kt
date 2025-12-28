package cl.clinipets.servicios.api

import cl.clinipets.servicios.application.ServicioMedicoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/servicios")
@Tag(name = "Servicios Médicos", description = "Endpoints para la gestión de servicios veterinarios")
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

    @Operation(summary = "Crear nuevo servicio (Staff/Admin)", operationId = "crearServicio")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping
    fun crear(@Valid @RequestBody request: ServicioCreateRequest): ResponseEntity<ServicioMedicoDto> {
        return ResponseEntity.ok(servicioMedicoService.crear(request))
    }

    @Operation(summary = "Actualizar servicio (Staff/Admin)", operationId = "actualizarServicio")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ServicioUpdateRequest
    ): ResponseEntity<ServicioMedicoDto> {
        return ResponseEntity.ok(servicioMedicoService.actualizar(id, request))
    }

    @Operation(summary = "Eliminar servicio (Staff/Admin)", operationId = "eliminarServicio")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @DeleteMapping("/{id}")
    fun eliminar(@PathVariable id: UUID): ResponseEntity<Unit> {
        servicioMedicoService.eliminar(id)
        return ResponseEntity.noContent().build()
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

    @Operation(summary = "Agregar regla de precio (Staff/Admin)", operationId = "agregarRegla")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping("/{id}/reglas-precio")
    fun agregarRegla(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReglaPrecioRequest
    ): ResponseEntity<ServicioMedicoDto> {
        return ResponseEntity.ok(servicioMedicoService.agregarRegla(id, request))
    }

    @Operation(summary = "Eliminar regla de precio (Staff/Admin)", operationId = "eliminarRegla")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @DeleteMapping("/{id}/reglas-precio/{reglaId}")
    fun eliminarRegla(
        @PathVariable id: UUID,
        @PathVariable reglaId: UUID
    ): ResponseEntity<ServicioMedicoDto> {
        return ResponseEntity.ok(servicioMedicoService.eliminarRegla(id, reglaId))
    }
}

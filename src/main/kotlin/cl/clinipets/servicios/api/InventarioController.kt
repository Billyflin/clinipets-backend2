package cl.clinipets.servicios.api

import cl.clinipets.servicios.application.InventarioService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1/inventario")
@Tag(name = "Inventario", description = "Endpoints para la gestión de insumos y stock")
class InventarioController(
    private val inventarioReportService: cl.clinipets.servicios.application.InventarioReportService,
    private val inventarioService: InventarioService
) {
    private val logger = LoggerFactory.getLogger(InventarioController::class.java)

    @Operation(summary = "Listar todos los insumos (Staff/Admin)", operationId = "listarInsumos")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/insumos")
    fun listarInsumos(): ResponseEntity<List<InsumoResponse>> {
        return ResponseEntity.ok(inventarioService.listarInsumos())
    }

    @Operation(summary = "Crear nuevo insumo (Staff/Admin)", operationId = "crearInsumo")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping("/insumos")
    fun crearInsumo(@Valid @RequestBody request: InsumoCreateRequest): ResponseEntity<InsumoResponse> {
        return ResponseEntity.ok(inventarioService.crearInsumo(request))
    }

    @Operation(summary = "Actualizar insumo (Staff/Admin)", operationId = "actualizarInsumo")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PutMapping("/insumos/{id}")
    fun actualizarInsumo(
        @PathVariable id: UUID,
        @Valid @RequestBody request: InsumoUpdateRequest
    ): ResponseEntity<InsumoResponse> {
        return ResponseEntity.ok(inventarioService.actualizarInsumo(id, request))
    }

    @Operation(summary = "Eliminar insumo (Staff/Admin)", operationId = "eliminarInsumo")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @DeleteMapping("/insumos/{id}")
    fun eliminarInsumo(@PathVariable id: UUID): ResponseEntity<Unit> {
        inventarioService.eliminarInsumo(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Agregar lote a un insumo (Staff/Admin)", operationId = "agregarLote")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping("/insumos/{id}/lotes")
    fun agregarLote(
        @PathVariable id: UUID,
        @Valid @RequestBody request: LoteCreateRequest
    ): ResponseEntity<InsumoResponse> {
        return ResponseEntity.ok(inventarioService.agregarLote(id, request))
    }

    @Operation(summary = "Ajustar stock de un lote (Staff/Admin)", operationId = "ajustarStockLote")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PatchMapping("/lotes/{id}")
    fun ajustarStockLote(
        @PathVariable id: UUID,
        @Valid @RequestBody request: LoteStockAjusteRequest
    ): ResponseEntity<InsumoResponse> {
        return ResponseEntity.ok(inventarioService.ajustarStockLote(id, request))
    }

    @Operation(summary = "Obtener alertas de stock bajo (Staff/Admin)", operationId = "obtenerAlertasStock")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/alertas")
    fun obtenerAlertas(): ResponseEntity<List<InsumoDetalladoDto>> {
        logger.info("[INVENTARIO_ALERTAS] Iniciando consulta de alertas")
        val alertas = inventarioReportService.generarAlertasStock()
        logger.info("[INVENTARIO_ALERTAS] Encontradas {} alertas de stock bajo", alertas.size)
        return ResponseEntity.ok(alertas)
    }

    @Operation(summary = "Obtener dashboard de vencimientos (Staff/Admin)", operationId = "obtenerVencimientos")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/vencimientos")
    fun obtenerVencimientos(): ResponseEntity<Map<String, List<InsumoResponse>>> {
        // Implementación rápida para el dashboard de vencimientos
        val todos = inventarioService.listarInsumos()
        val hoy = LocalDate.now()

        val vencimientos = mapOf(
            "proximos_7_dias" to todos.filter { i ->
                i.lotes.any { l ->
                    !l.estaVencido && l.fechaVencimiento.isBefore(
                        hoy.plusDays(7)
                    )
                }
            },
            "proximos_15_dias" to todos.filter { i ->
                i.lotes.any { l ->
                    !l.estaVencido && l.fechaVencimiento.isBefore(
                        hoy.plusDays(15)
                    )
                }
            },
            "proximos_30_dias" to todos.filter { i ->
                i.lotes.any { l ->
                    !l.estaVencido && l.fechaVencimiento.isBefore(
                        hoy.plusDays(30)
                    )
                }
            }
        )
        return ResponseEntity.ok(vencimientos)
    }
}

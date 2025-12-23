package cl.clinipets.servicios.api

import cl.clinipets.servicios.domain.InsumoRepository
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/inventario")
class InventarioController(
    private val inventarioReportService: cl.clinipets.servicios.application.InventarioReportService
) {
    private val logger = LoggerFactory.getLogger(InventarioController::class.java)

    @Operation(summary = "Obtener alertas de stock bajo (Staff/Admin)", operationId = "obtenerAlertasStock")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/alertas")
    fun obtenerAlertas(): ResponseEntity<List<InsumoDetalladoDto>> {
        logger.info("[INVENTARIO_ALERTAS] Iniciando consulta de alertas")
        val alertas = inventarioReportService.generarAlertasStock()
        logger.info("[INVENTARIO_ALERTAS] Encontradas {} alertas de stock bajo", alertas.size)
        return ResponseEntity.ok(alertas)
    }
}

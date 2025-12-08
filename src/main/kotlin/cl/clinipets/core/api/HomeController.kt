package cl.clinipets.core.api

import cl.clinipets.core.ia.VeterinaryAgentService
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.openapi.models.DashboardResponse
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/home")
class HomeController(
    private val veterinaryAgentService: VeterinaryAgentService
) {
    private val logger = LoggerFactory.getLogger(HomeController::class.java)

    @Operation(summary = "Obtener dashboard personalizado", operationId = "obtenerDashboard")
    @GetMapping("/dashboard")
    fun obtenerDashboard(
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<DashboardResponse> {
        logger.info("[HOME_DASHBOARD] Request user {}", principal.email)
        val dashboard = veterinaryAgentService.generarDashboard(principal.userId)
        return ResponseEntity.ok(dashboard)
    }
}

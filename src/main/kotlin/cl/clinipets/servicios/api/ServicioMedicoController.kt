package cl.clinipets.servicios.api

import cl.clinipets.servicios.application.ServicioMedicoService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController

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
}

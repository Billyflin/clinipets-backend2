package cl.clinipets.maestros.api

import cl.clinipets.maestros.application.RazasService
import cl.clinipets.veterinaria.domain.Especie
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/maestros")
class MaestrosController(
    private val razasService: RazasService
) {
    private val logger = LoggerFactory.getLogger(MaestrosController::class.java)

    @Operation(summary = "Listar razas", description = "Devuelve una lista de razas, opcionalmente filtrada por especie")
    @GetMapping("/razas")
    fun listarRazas(
        @RequestParam(required = false) especie: Especie?
    ): ResponseEntity<List<String>> {
        logger.info("[LISTAR_RAZAS] Request recibida. Filtro Especie: {}", especie ?: "TODAS")
        val response = razasService.getRazas(especie)
        logger.info("[LISTAR_RAZAS] Fin request - Encontradas: {}", response.size)
        return ResponseEntity.ok(response)
    }
}

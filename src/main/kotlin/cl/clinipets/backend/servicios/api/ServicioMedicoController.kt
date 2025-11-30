package cl.clinipets.backend.servicios.api

import cl.clinipets.backend.servicios.application.ServicioMedicoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/servicios")
class ServicioMedicoController(
    private val servicioMedicoService: ServicioMedicoService
) {
    @GetMapping
    fun listar(): ResponseEntity<List<ServicioMedicoDto>> =
        ResponseEntity.ok(servicioMedicoService.listarActivos())
}

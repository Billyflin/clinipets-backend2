package cl.clinipets.backend.veterinaria.api

import cl.clinipets.backend.veterinaria.application.MascotaService
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mascotas")
class MascotaController(
    private val mascotaService: MascotaService
) {
    @Operation(summary = "Crear mascota", operationId = "crearMascota")
    @PostMapping
    fun crear(
        @Valid @RequestBody request: MascotaCreateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MascotaResponse> =
        ResponseEntity.ok(mascotaService.crear(request, principal))

    @Operation(summary = "Listar mascotas", operationId = "listarMascotas")
    @GetMapping
    fun listar(
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<MascotaResponse>> =
        ResponseEntity.ok(mascotaService.listar(principal))
}

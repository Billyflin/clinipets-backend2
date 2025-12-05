package cl.clinipets.veterinaria.api

import cl.clinipets.veterinaria.application.MascotaService
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/mascotas")
class MascotaController(
    private val mascotaService: MascotaService
) {
    private val logger = LoggerFactory.getLogger(MascotaController::class.java)

    @Operation(summary = "Crear mascota", operationId = "crearMascota")
    @PostMapping
    fun crear(
        @Valid @RequestBody request: MascotaCreateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MascotaResponse> {
        logger.info("[CREAR_MASCOTA] Inicio request. Tutor: {}", principal.email)
        val response = mascotaService.crear(request, principal)
        logger.info("[CREAR_MASCOTA] Fin request - Exitoso. MascotaID: {}", response.id)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Listar mascotas", operationId = "listarMascotas")
    @GetMapping
    fun listar(
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<MascotaResponse>> {
        logger.info("[LISTAR_MASCOTAS] Inicio request. Tutor: {}", principal.email)
        val response = mascotaService.listar(principal)
        logger.info("[LISTAR_MASCOTAS] Fin request - Encontradas: {}", response.size)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Obtener mascota", operationId = "obtenerMascota")
    @GetMapping("/{id}")
    fun obtener(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MascotaResponse> {
        logger.info("[OBTENER_MASCOTA] Inicio request. ID: {}, Tutor: {}", id, principal.email)
        val response = mascotaService.obtener(id, principal)
        logger.info("[OBTENER_MASCOTA] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Actualizar mascota", operationId = "actualizarMascota")
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MascotaUpdateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MascotaResponse> {
        logger.info("[ACTUALIZAR_MASCOTA] Inicio request. ID: {}, Tutor: {}", id, principal.email)
        val response = mascotaService.actualizar(id, request, principal)
        logger.info("[ACTUALIZAR_MASCOTA] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Eliminar mascota", operationId = "eliminarMascota")
    @DeleteMapping("/{id}")
    fun eliminar(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<Void> {
        logger.info("[ELIMINAR_MASCOTA] Inicio request. ID: {}, Tutor: {}", id, principal.email)
        mascotaService.eliminar(id, principal)
        logger.info("[ELIMINAR_MASCOTA] Fin request - Exitoso")
        return ResponseEntity.noContent().build()
    }
}

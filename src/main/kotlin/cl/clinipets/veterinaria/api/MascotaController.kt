package cl.clinipets.veterinaria.api

import cl.clinipets.veterinaria.application.MascotaService
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Mascota creada"),
        ApiResponse(responseCode = "400", description = "Datos inválidos")
    )
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
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Lista de mascotas")
    )
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
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Mascota encontrada"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
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
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Mascota actualizada"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
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

    @Operation(summary = "Actualizar datos clínicos (Staff/Admin)", operationId = "actualizarDatosClinicos")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Datos clínicos actualizados"),
        ApiResponse(responseCode = "403", description = "Sin permisos"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @PatchMapping("/{id}/clinico")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    fun actualizarDatosClinicos(
        @PathVariable id: UUID,
        @RequestBody request: MascotaClinicalUpdateRequest
    ): ResponseEntity<MascotaResponse> {
        logger.info("[ACTUALIZAR_CLINICO] Inicio request. ID: {}", id)
        val response = mascotaService.actualizarDatosClinicos(id, request)
        logger.info("[ACTUALIZAR_CLINICO] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Eliminar mascota", operationId = "eliminarMascota")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Mascota eliminada"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
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
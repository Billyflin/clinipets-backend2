package cl.clinipets.identity.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.application.AuthService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @Operation(summary = "Login/Sync con Firebase", operationId = "firebaseAuth")
    @PostMapping("/firebase")
    fun firebaseAuth(@AuthenticationPrincipal principal: JwtPayload): ResponseEntity<ProfileResponse> {
        // El usuario ya fue autenticado y sincronizado por FirebaseFilter antes de llegar aquí.
        // Solo necesitamos retornar su perfil.
        logger.info("[AUTH] Login exitoso para: ${principal.email}")
        return ResponseEntity.ok(authService.me(principal))
    }

    @Operation(summary = "Obtener perfil actual", operationId = "getProfile")
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: JwtPayload): ResponseEntity<ProfileResponse> {
        return ResponseEntity.ok(authService.me(principal))
    }

    @Operation(summary = "Actualizar perfil de usuario", operationId = "updateProfile")
    @PutMapping("/me")
    fun updateMe(
        @AuthenticationPrincipal principal: JwtPayload,
        @Valid @RequestBody request: UserUpdateRequest
    ): ResponseEntity<ProfileResponse> {
        return ResponseEntity.ok(authService.updateProfile(principal.userId, request))
    }
}
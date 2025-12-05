package cl.clinipets.identity.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.application.AuthService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @Operation(summary = "Refrescar token", operationId = "refreshToken")
    @PostMapping("/refresh")
    fun refresh(@RequestParam("token") refreshToken: String?): ResponseEntity<TokenResponse> {
        logger.info("[REFRESH] Inicio request")
        val response = authService.refresh(refreshToken)
        logger.info("[REFRESH] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Login con Google", operationId = "loginGoogle")
    @PostMapping("/google")
    fun google(@Valid @RequestBody request: GoogleLoginRequest): ResponseEntity<TokenResponse> {
        logger.info("[GOOGLE_LOGIN] Inicio request")
        val response = authService.loginWithGoogle(request.idToken)
        logger.info("[GOOGLE_LOGIN] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Obtener perfil", operationId = "getProfile")
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: JwtPayload): ResponseEntity<ProfileResponse> {
        logger.info("[ME] Inicio request - User: {}", principal.email)
        val response = authService.me(principal)
        logger.info("[ME] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }
}

package cl.clinipets.identity.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.application.AuthService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
        val response = authService.loginWithGoogle(request.idToken, request.phone)
        logger.info("[GOOGLE_LOGIN] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Solicitar OTP por teléfono (login/registro sin contraseña)", operationId = "requestOtp")
    @PostMapping("/otp/request")
    fun requestOtp(@Valid @RequestBody request: OtpRequest): ResponseEntity<Map<String, String>> {
        logger.info("[OTP_REQUEST] Inicio request. Payload: {}", request)
        authService.requestOtp(request.phone)
        return ResponseEntity.ok(mapOf("status" to "sent"))
    }

    @Operation(summary = "Validar OTP y obtener tokens", operationId = "verifyOtp")
    @PostMapping("/otp/verify")
    fun verifyOtp(@Valid @RequestBody request: OtpVerifyRequest): ResponseEntity<TokenResponse> {
        logger.info("[OTP_VERIFY] Inicio request. Payload: {}", request)
        val response = authService.verifyOtp(request.phone, request.code, request.name)
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

    @Operation(summary = "Actualizar perfil de usuario", operationId = "updateProfile")
    @PutMapping("/me")
    fun updateMe(
        @AuthenticationPrincipal principal: JwtPayload,
        @Valid @RequestBody request: UserUpdateRequest
    ): ResponseEntity<ProfileResponse> {
        logger.info("[UPDATE_ME] Inicio request - User: {}", principal.email)
        val response = authService.updateProfile(principal.userId, request)
        logger.info("[UPDATE_ME] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Vincular cuenta de Google", operationId = "linkGoogleAccount")
    @PostMapping("/link/google")
    fun linkGoogleAccount(
        @AuthenticationPrincipal principal: JwtPayload,
        @Valid @RequestBody request: GoogleLoginRequest
    ): ResponseEntity<ProfileResponse> {
        logger.info("[LINK_GOOGLE] Inicio request - User: {}", principal.email)
        val response = authService.linkGoogleAccount(principal.userId, request.idToken)
        logger.info("[LINK_GOOGLE] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }
}

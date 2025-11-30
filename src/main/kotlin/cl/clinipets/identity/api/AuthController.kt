package cl.clinipets.identity.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.application.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/refresh")
    fun refresh(@RequestParam("token") refreshToken: String?): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(authService.refresh(refreshToken))

    @PostMapping("/google")
    fun google(@Valid @RequestBody request: GoogleLoginRequest): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(authService.loginWithGoogle(request.idToken))

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: JwtPayload): ResponseEntity<ProfileResponse> =
        ResponseEntity.ok(authService.me(principal))
}

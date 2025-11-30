package cl.clinipets.identity.api

import cl.clinipets.identity.domain.UserRole
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)

data class GoogleLoginRequest(
    @field:NotBlank(message = "El idToken es obligatorio")
    val idToken: String
)

data class ProfileResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val role: UserRole
)

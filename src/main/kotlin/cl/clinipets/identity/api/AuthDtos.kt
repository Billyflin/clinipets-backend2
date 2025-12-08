package cl.clinipets.identity.api

import cl.clinipets.identity.domain.UserRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)

data class GoogleLoginRequest(
    @field:NotBlank(message = "El idToken es obligatorio")
    val idToken: String,
    val phone: String? = null
)

data class ProfileResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val role: UserRole,
    val phone: String?,
    val address: String?
)

data class UserUpdateRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    @field:Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @field:Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "El nombre solo puede contener letras y espacios")
    val name: String,

    @field:Size(max = 20, message = "El teléfono no puede exceder los 20 caracteres")
    val phone: String?,

    @field:Size(max = 255, message = "La dirección no puede exceder los 255 caracteres")
    val address: String?
)

data class OtpRequest(
    @field:NotBlank(message = "El teléfono es obligatorio")
    val phone: String
)

data class OtpVerifyRequest(
    @field:NotBlank(message = "El teléfono es obligatorio")
    val phone: String,

    @field:NotBlank(message = "El código es obligatorio")
    val code: String,

    val name: String? = null
)

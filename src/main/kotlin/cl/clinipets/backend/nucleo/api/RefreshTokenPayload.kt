package cl.clinipets.backend.nucleo.api

import java.util.*

/** Datos del refresh token. */
data class RefreshTokenPayload(
    val subject: UUID,
    val email: String,
    val roles: List<String> = emptyList(),
    val iat: Date = Date(),
    val exp: Date? = null,
    val typ: String = "refresh"
) {
    val isExpired: Boolean
        get() = exp?.before(Date()) ?: false
}


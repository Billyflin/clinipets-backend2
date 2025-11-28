package cl.clinipets.backend.nucleo.api

import java.util.*

/** Datos mínimos del token propio. */
data class TokenPayload(
    val subject: UUID,
    val email: String,
    val roles: List<String>,
    val nombre: String?,
    val fotoUrl: String?,
    val iat: Date = Date(),
    val exp: Date? = null
) {
    val isExpired: Boolean
        get() = exp?.before(Date()) ?: false

    fun hasRole(role: String): Boolean =
        roles.any { it.equals(role, ignoreCase = true) }

    fun hasAnyRole(vararg roles: String): Boolean =
        roles.any { hasRole(it) }
}

package cl.clinipets.core.security

import cl.clinipets.identity.domain.UserRole
import java.time.Instant
import java.util.UUID

data class JwtPayload(
    val userId: UUID,
    val email: String,
    val role: UserRole,
    val expiresAt: Instant
)

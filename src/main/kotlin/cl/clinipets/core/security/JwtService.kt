package cl.clinipets.core.security

import cl.clinipets.core.config.JwtProperties
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRole
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.UUID
import javax.crypto.SecretKey

data class JwtPayload(
    val userId: UUID,
    val email: String,
    val role: UserRole,
    val expiresAt: Instant
)

@Component
class JwtService(
    private val props: JwtProperties
) {
    private val clock: Clock = Clock.systemUTC()
    private val accessKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret))
    }
    private val refreshKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.refreshSecret))
    }

    fun generateAccessToken(user: User): String = buildToken(user, props.expirationMinutes, accessKey)

    fun generateRefreshToken(user: User): String =
        buildToken(user, props.refreshExpirationHours * 60, refreshKey)

    fun parseAccessToken(token: String): JwtPayload? = parse(token, accessKey)

    fun parseRefreshToken(token: String): JwtPayload? = parse(token, refreshKey)

    private fun buildToken(user: User, minutes: Long, key: SecretKey): String {
        val now = Instant.now(clock)
        val expiry = now.plus(minutes, ChronoUnit.MINUTES)
        return Jwts.builder()
            .subject(user.id?.toString() ?: "")
            .issuer(props.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("email", user.email)
            .claim("name", user.name)
            .claim("role", user.role.name)
            .signWith(key)
            .compact()
    }

    private fun parse(token: String, key: SecretKey): JwtPayload? {
        return try {
            val claims = Jwts.parser()
                .requireIssuer(props.issuer)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            val id = claims.subject?.let {
                try { UUID.fromString(it) } catch (e: Exception) { null }
            } ?: return null
            val email = claims["email"] as? String ?: return null
            val role = (claims["role"] as? String)?.let { UserRole.valueOf(it) } ?: return null
            JwtPayload(
                userId = id,
                email = email,
                role = role,
                expiresAt = claims.expiration.toInstant()
            )
        } catch (ex: Exception) {
            null
        }
    }
}

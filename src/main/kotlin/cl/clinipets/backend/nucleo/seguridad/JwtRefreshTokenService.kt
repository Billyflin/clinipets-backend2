package cl.clinipets.backend.nucleo.seguridad

import cl.clinipets.backend.nucleo.api.RefreshTokenPayload
import cl.clinipets.backend.nucleo.api.RefreshTokenService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.*

@Component
class JwtRefreshTokenService(
    @Value("\${jwt.refresh.secret}") private val secretB64: String,
    @Value("\${jwt.issuer}") private val issuer: String,
    @Value("\${jwt.refresh.expiration-days}") private val expirationDays: Long,
    @Value("\${jwt.refresh.cookie-name}") private val cookieName: String,
    @Value("\${jwt.refresh.cookie-domain:}") private val cookieDomain: String,
    @Value("\${jwt.refresh.cookie-secure:false}") private val cookieSecure: Boolean,
    @Value("\${jwt.refresh.cookie-same-site:Lax}") private val cookieSameSite: String,
    @Value("\${jwt.refresh.cookie-path:/}") private val cookiePath: String
) : RefreshTokenService {
    private val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretB64))

    override fun issue(userId: UUID, email: String, roles: List<String>): String {
        require(email.isNotBlank()) { "El email no puede estar vacío" }
        val now = Date.from(Instant.now())
        val exp = Date.from(Instant.now().plusSeconds(Duration.ofDays(expirationDays).seconds))
        return Jwts.builder()
            .subject(userId.toString())
            .issuer(issuer)
            .issuedAt(now)
            .expiration(exp)
            .claim("email", email)
            .claim("roles", roles)
            .claim("typ", "refresh")
            .signWith(key)
            .compact()
    }

    override fun parse(token: String): RefreshTokenPayload {
        try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .payload

            val userId = try {
                UUID.fromString(claims.subject)
            } catch (e: IllegalArgumentException) {
                throw BadCredentialsException("ID de usuario inválido en refresh")
            }

            val email = claims["email"] as? String
                ?: throw BadCredentialsException("Email faltante en refresh")

            val typ = claims["typ"] as? String
            if (typ != null && typ != "refresh") {
                throw BadCredentialsException("Token no es de tipo refresh")
            }

            val roles = (claims["roles"] as? Collection<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            return RefreshTokenPayload(
                subject = userId,
                email = email,
                roles = roles,
                iat = claims.issuedAt,
                exp = claims.expiration
            )
        } catch (e: ExpiredJwtException) {
            throw BadCredentialsException("Refresh token expirado")
        } catch (e: JwtException) {
            throw BadCredentialsException("Refresh token inválido")
        } catch (e: Exception) {
            throw BadCredentialsException("Error al procesar refresh: ${e.message}")
        }
    }

    override fun setCookie(res: HttpServletResponse, token: String) {
        val builder = ResponseCookie.from(cookieName, token)
            .httpOnly(true)
            .secure(cookieSecure)
            .path(cookiePath)
            .maxAge(Duration.ofDays(expirationDays))
            .sameSite(cookieSameSite)
        if (cookieDomain.isNotBlank()) builder.domain(cookieDomain)
        val cookie = builder.build()
        res.addHeader("Set-Cookie", cookie.toString())
    }

    override fun clearCookie(res: HttpServletResponse) {
        val builder = ResponseCookie.from(cookieName, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path(cookiePath)
            .maxAge(Duration.ZERO)
            .sameSite(cookieSameSite)
        if (cookieDomain.isNotBlank()) builder.domain(cookieDomain)
        val cookie = builder.build()
        res.addHeader("Set-Cookie", cookie.toString())
    }
}


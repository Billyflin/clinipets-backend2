package cl.clinipets.backend.nucleo.seguridad

import cl.clinipets.backend.nucleo.api.TokenPayload
import cl.clinipets.backend.nucleo.api.TokenService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class JwtTokenService(
    @param:Value("\${jwt.secret}") private val secretB64: String,
    @param:Value("\${jwt.issuer}") private val issuer: String,
    @param:Value("\${jwt.expiration-minutes}") private val expMinutes: Long
) : TokenService {
    private val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretB64.trim()))

    override fun issue(userId: UUID, email: String, roles: List<String>, nombre: String?, fotoUrl: String?): String {
        require(email.isNotBlank()) { "El email no puede estar vacío" }

        val now = Date.from(Instant.now())
        val exp = Date.from(Instant.now().plusSeconds(expMinutes * 60))

        return Jwts.builder()
            .subject(userId.toString())
            .issuer(issuer)
            .issuedAt(now)
            .expiration(exp)
            .claim("email", email)
            .claim("roles", roles)
            .claim("nombre", nombre)
            .claim("fotoUrl", fotoUrl)
            .signWith(key)
            .compact()
    }

    override fun parse(token: String): TokenPayload {
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
                throw BadCredentialsException("ID de usuario inválido en token")
            }

            val email = claims["email"] as? String
                ?: throw BadCredentialsException("Email faltante en token")

            val roles = (claims["roles"] as? Collection<*>)?.mapNotNull {
                it?.toString()?.takeIf { role -> role.isNotBlank() }
            } ?: emptyList()

            return TokenPayload(
                subject = userId,
                email = email,
                roles = roles,
                nombre = claims["nombre"] as? String,
                fotoUrl = claims["fotoUrl"] as? String,
                iat = claims.issuedAt,
                exp = claims.expiration
            )
        } catch (e: ExpiredJwtException) {
            throw BadCredentialsException("Token expirado")
        } catch (e: JwtException) {
            throw BadCredentialsException("Token inválido")
        } catch (e: Exception) {
            throw BadCredentialsException("Error al procesar token: ${e.message}")
        }
    }
}

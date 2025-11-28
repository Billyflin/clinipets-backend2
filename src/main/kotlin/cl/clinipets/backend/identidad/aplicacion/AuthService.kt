package cl.clinipets.backend.identidad.aplicacion

import cl.clinipets.backend.identidad.dominio.Rol
import cl.clinipets.backend.identidad.dominio.Roles
import cl.clinipets.backend.identidad.dominio.Usuario
import cl.clinipets.backend.identidad.infraestructura.RolRepository
import cl.clinipets.backend.identidad.infraestructura.UsuarioRepository
import cl.clinipets.backend.nucleo.api.RefreshTokenService
import cl.clinipets.backend.nucleo.api.TokenService
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class AuthService(
    private val google: GoogleTokenVerifier,
    private val usuarioRepo: UsuarioRepository,
    private val rolRepo: RolRepository,
    private val refreshTokens: RefreshTokenService,
    private val tokens: TokenService
) {
    @Schema(
        description = "Payload enviado por el cliente móvil luego de autenticarse con Google Sign-In.",
        example = """{"idToken":"eyJhbGciOiJSUzI1NiIsImtpZCI6InNvbWUta2V5In0..."}"""
    )
    data class GoogleLoginRequest(
        @Schema(description = "ID Token de Google con email verificado", required = true)
        val idToken: String
    )

    @Schema(description = "Tokens de sesión emitidos para el cliente.")
    data class SessionTokens(
        @Schema(description = "JWT de acceso firmado por el backend", required = true)
        val accessToken: String,
        @Schema(description = "Refresh token firmado, útil para rotar la sesión", required = true)
        val refreshToken: String,
        @Schema(description = "Fecha/hora de expiración del JWT de acceso", format = "date-time", required = true)
        val accessTokenExpiresAt: Instant,
        @Schema(description = "Fecha/hora en que expira el refresh token", format = "date-time")
        val refreshTokenExpiresAt: Instant?,
        @Schema(description = "Perfil básico del usuario autenticado", required = true)
        val usuario: UsuarioInfo
    )

    @Schema(description = "Información del usuario autenticado expuesta al cliente.")
    data class UsuarioInfo(
        @Schema(description = "Identificador del usuario", format = "uuid", required = true)
        val id: UUID,
        @Schema(description = "Email verificado del usuario", format = "email", required = true)
        val email: String,
        @Schema(description = "Nombre visible del usuario")
        val nombre: String?,
        @Schema(description = "URL del avatar o foto del usuario")
        val fotoUrl: String?,
        @Schema(description = "Roles otorgados al usuario", example = "[\"CLIENTE\"]", required = true)
        val roles: List<String>
    )

    @Transactional
    fun loginGoogle(req: GoogleLoginRequest): SessionTokens {
        val payload = try {
            google.verify(req.idToken)
        } catch (e: Exception) {
            throw BadCredentialsException("Token de Google inválido: ${e.message}")
        }

        val email = payload.email ?: throw BadCredentialsException("El token no contiene email verificado")
        val nombre = payload["name"] as? String
        val foto = payload["picture"] as? String

        val rolCliente = rolRepo.findByNombre(Roles.CLIENTE)
            ?: rolRepo.save(Rol(nombre = Roles.CLIENTE))
        // Buscar o crear usuario
        val user = usuarioRepo.findByEmail(email)?.apply {
            actualizarPerfil(nombre, foto)
            asegurarRol(rolCliente)
        } ?: usuarioRepo.save(
            Usuario(
                email = email,
                nombre = nombre,
                fotoUrl = foto,
                roles = mutableSetOf(rolCliente)
            )
        )

        return buildSessionTokens(user)
    }

    @Transactional(readOnly = true)
    fun refresh(refreshToken: String): SessionTokens {
        val payload = try {
            refreshTokens.parse(refreshToken)
        } catch (ex: BadCredentialsException) {
            throw ex
        } catch (ex: Exception) {
            throw BadCredentialsException("Refresh inválido: ${ex.message}")
        }

        if (payload.isExpired) {
            throw BadCredentialsException("Refresh expirado")
        }

        val user = usuarioRepo.findById(payload.subject).orElseGet {
            usuarioRepo.findByEmail(payload.email)
                ?: throw BadCredentialsException("Usuario no encontrado para refresh")
        }

        return buildSessionTokens(user)
    }

    private fun buildSessionTokens(user: Usuario): SessionTokens {
        val roles = user.roles.map { it.nombre }.ifEmpty { listOf(Roles.CLIENTE) }
        // Si el usuario es provisional, usamos su ID o un placeholder para el "email" en el token
        val emailForToken = user.email ?: "provisional-${user.id}"
        
        val accessToken = tokens.issue(
            userId = user.id!!,
            email = emailForToken,
            roles = roles,
            nombre = user.nombre,
            fotoUrl = user.fotoUrl
        )
        val refreshToken = refreshTokens.issue(
            userId = user.id!!,
            email = emailForToken,
            roles = roles
        )
        val accessPayload = tokens.parse(accessToken)
        val refreshPayload = refreshTokens.parse(refreshToken)
        val accessExpiresAt = accessPayload.exp?.toInstant()
            ?: throw IllegalStateException("Token sin expiración emitido")

        return SessionTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessExpiresAt,
            refreshTokenExpiresAt = refreshPayload.exp?.toInstant(),
            usuario = UsuarioInfo(
                id = user.id!!,
                email = emailForToken,
                nombre = user.nombre,
                fotoUrl = user.fotoUrl,
                roles = roles
            )
        )
    }
}

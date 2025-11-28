package cl.clinipets.backend.identidad.web

import cl.clinipets.backend.identidad.aplicacion.AuthService
import cl.clinipets.backend.nucleo.api.RefreshTokenService
import cl.clinipets.backend.nucleo.api.TokenPayload
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Endpoints de autenticación y gestión de sesión")
class AuthController(
    private val auth: AuthService,
    private val refresh: RefreshTokenService,
    @Value("\${jwt.cookie-name:clinipets-refresh}") private val refreshCookieName: String
) {

    @Schema(description = "Respuesta devuelta tras un login exitoso.")
    data class LoginResponse(
        @Schema(description = "JWT de acceso emitido por el backend", required = true)
        val token: String,
        @Schema(description = "Expiración del JWT de acceso", format = "date-time", required = true)
        val expiresAt: Instant,
        @Schema(description = "Expiración del refresh token", format = "date-time")
        val refreshExpiresAt: Instant?,
        @Schema(description = "Perfil del usuario autenticado", required = true)
        val usuario: AuthService.UsuarioInfo
    )

    @PostMapping("/google")
    @Operation(
        summary = "Autenticación con Google",
        description = """
            Autentica un usuario mediante Google Sign-In. 
            Intercambia un Google ID Token válido por un JWT propio del sistema.
            Si el usuario no existe, se crea automáticamente.
            También emite un refresh token en cookie HttpOnly.
        """,
        operationId = "authLoginGoogle",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Token de Google emitido por el cliente",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = AuthService.GoogleLoginRequest::class),
                examples = [ExampleObject(
                    name = "LoginGoogle",
                    summary = "Petición con ID Token válido",
                    value = """{"idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..."}"""
                )]
            )]
        )
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Autenticación exitosa",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = LoginResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Token mal formado",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Token de Google inválido o expirado",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))]
            )
        ]
    )
    fun google(@RequestBody body: AuthService.GoogleLoginRequest, res: HttpServletResponse): LoginResponse {
        val session = auth.loginGoogle(body)
        refresh.setCookie(res, session.refreshToken)
        return LoginResponse(
            token = session.accessToken,
            expiresAt = session.accessTokenExpiresAt,
            refreshExpiresAt = session.refreshTokenExpiresAt,
            usuario = session.usuario
        )
    }


    @Schema(description = "Respuesta al consultar el perfil actual.")
    data class MeResponse(
        @Schema(description = "Indica si existe una sesión autenticada", required = true)
        val authenticated: Boolean,
        @Schema(description = "Identificador del usuario autenticado", format = "uuid")
        val id: UUID? = null,
        @Schema(description = "Email asociado a la sesión")
        val email: String? = null,
        @Schema(description = "Roles activos del usuario")
        val roles: List<String>? = null,
        @Schema(description = "Nombre visible del usuario")
        val nombre: String? = null,
        @Schema(description = "URL del avatar o foto del usuario")
        val fotoUrl: String? = null,
    )

    @GetMapping("/me")
    @Operation(
        summary = "Obtener perfil actual",
        description = """
            Devuelve información básica del usuario autenticado.
            Si no hay token o es inválido, devuelve authenticated=false.
        """,
        operationId = "authMe",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Perfil obtenido exitosamente",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MeResponse::class)
                )]
            )
        ]
    )
    fun me(@AuthenticationPrincipal principal: TokenPayload?): MeResponse =
        if (principal == null) MeResponse(authenticated = false)
        else MeResponse(
            authenticated = true,
            id = principal.subject,
            email = principal.email,
            roles = principal.roles,
            nombre = principal.nombre,
            fotoUrl = principal.fotoUrl
        )

    @Schema(description = "Respuesta tras rotar el token de acceso.")
    data class RefreshResponse(
        @Schema(description = "Nuevo JWT de acceso", required = true)
        val token: String,
        @Schema(description = "Expiración del nuevo JWT de acceso", format = "date-time", required = true)
        val expiresAt: Instant,
        @Schema(description = "Perfil del usuario autenticado", required = true)
        val usuario: AuthService.UsuarioInfo
    )

    @Schema(description = "Respuesta estándar de error.")
    data class ErrorResponse(
        @Schema(description = "Mensaje legible del error", required = true)
        val error: String
    )

    @PostMapping("/refresh")
    @Operation(
        summary = "Refrescar sesión",
        description = "Lee el refresh token de la cookie HttpOnly y entrega un nuevo access token (y rota el refresh).",
        operationId = "authRefresh",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Token rotado correctamente",
                content = [Content(schema = Schema(implementation = RefreshResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Refresh token ausente, inválido o expirado",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun refreshToken(req: HttpServletRequest, res: HttpServletResponse): ResponseEntity<Any> {
        val cookie = req.cookies?.firstOrNull { it.name == refreshCookieName }
            ?: return unauthorized("No hay refresh token")
        val session = try {
            auth.refresh(cookie.value)
        } catch (ex: BadCredentialsException) {
            return unauthorized(ex.message ?: "Refresh inválido")
        }
        refresh.setCookie(res, session.refreshToken)
        return ResponseEntity.ok(
            RefreshResponse(
                token = session.accessToken,
                expiresAt = session.accessTokenExpiresAt,
                usuario = session.usuario
            )
        )
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Cerrar sesión",
        description = "Elimina la cookie de refresh y cierra la sesión en el cliente.",
        operationId = "authLogout"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Sesión cerrada correctamente")
        ]
    )
    fun logout(res: HttpServletResponse): Map<String, Any> {
        refresh.clearCookie(res)
        return mapOf("ok" to true)
    }

    private fun unauthorized(message: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse(message))
}

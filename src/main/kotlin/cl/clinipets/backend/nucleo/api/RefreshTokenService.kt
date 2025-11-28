package cl.clinipets.backend.nucleo.api

import jakarta.servlet.http.HttpServletResponse
import java.util.*

interface RefreshTokenService {
    fun issue(userId: UUID, email: String, roles: List<String> = emptyList()): String
    fun parse(token: String): RefreshTokenPayload

    /** Agrega cookie httpOnly con el refresh token al response. */
    fun setCookie(res: HttpServletResponse, token: String)

    /** Elimina la cookie de refresh del response. */
    fun clearCookie(res: HttpServletResponse)
}


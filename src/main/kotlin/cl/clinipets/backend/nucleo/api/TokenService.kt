package cl.clinipets.backend.nucleo.api

import java.util.*

/** Puerto de emisión/validación de tokens de la plataforma. */
interface TokenService {
    fun issue(
        userId: UUID,
        email: String,
        roles: List<String>,
        nombre: String? = null,
        fotoUrl: String? = null
    ): String

    fun parse(token: String): TokenPayload
}

package cl.clinipets.backend.nucleo.api

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.*

@Component
class Actual {
    private fun getPayload(): TokenPayload? =
        SecurityContextHolder.getContext().authentication?.principal as? TokenPayload

    fun usuarioId(): UUID? = getPayload()?.subject

    fun userId(): UUID = usuarioId() ?: throw IllegalStateException("Usuario no autenticado")

    fun email(): String? = getPayload()?.email

    fun nombre(): String? = getPayload()?.nombre

    fun fotoUrl(): String? = getPayload()?.fotoUrl

    fun roles(): List<String> = getPayload()?.roles ?: emptyList()

    fun hasRole(rol: String): Boolean = getPayload()?.hasRole(rol) ?: false

    fun hasAnyRole(vararg roles: String): Boolean = getPayload()?.hasAnyRole(*roles) ?: false

    fun isAuthenticated(): Boolean = getPayload() != null

    fun requireAuthenticated(): TokenPayload =
        getPayload() ?: throw IllegalStateException("Usuario no autenticado")
}
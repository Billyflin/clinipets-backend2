package cl.clinipets.backend.nucleo.seguridad

import cl.clinipets.backend.identidad.dominio.Roles
import cl.clinipets.backend.nucleo.api.TokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(private val tokens: TokenService) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        try {
            val header = req.getHeader("Authorization")
            if (header?.startsWith("Bearer ") != true) {
                SecurityContextHolder.clearContext()
                return chain.doFilter(req, res)
            }

            val token = header.removePrefix("Bearer ").trim()
            val payload = try {
                tokens.parse(token)
            } catch (e: Exception) {
                // Token inválido: limpiar contexto y continuar
                SecurityContextHolder.clearContext()
                return chain.doFilter(req, res)
            }

            // Verificar expiración
            if (payload.isExpired) {
                // Token expirado: limpiar contexto y continuar
                SecurityContextHolder.clearContext()
                return chain.doFilter(req, res)
            }

            // Asegurar que haya al menos un rol
            val roles = payload.roles.ifEmpty { listOf(Roles.CLIENTE) }

            // Normalizar roles y crear authorities
            val authorities = roles.map { role ->
                val normalizedRole = role.removePrefix("ROLE_").uppercase()
                SimpleGrantedAuthority("ROLE_$normalizedRole")
            }

            val auth = UsernamePasswordAuthenticationToken(payload, null, authorities)
            auth.details = WebAuthenticationDetailsSource().buildDetails(req)
            SecurityContextHolder.getContext().authentication = auth

            chain.doFilter(req, res)
        } catch (e: Exception) {
            SecurityContextHolder.clearContext()
            res.status = HttpServletResponse.SC_UNAUTHORIZED
            res.contentType = "application/json"
            res.writer.write("""{"error": "Error de autenticación"}""")
        }
    }
}

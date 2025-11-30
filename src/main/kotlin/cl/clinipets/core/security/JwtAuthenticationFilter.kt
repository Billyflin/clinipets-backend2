package cl.clinipets.core.security

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
        val bearer = authorization?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        val token = bearer?.removePrefix("Bearer ")?.trim()

        if (!token.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            val payload = jwtService.parseAccessToken(token)
            if (payload != null) {
                val auth = UsernamePasswordAuthenticationToken(
                    payload,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${payload.role.name}"))
                )
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath.startsWith("/actuator") ||
            request.servletPath.equals("/api/auth/refresh") ||
            request.servletPath.equals("/api/auth/google")
}

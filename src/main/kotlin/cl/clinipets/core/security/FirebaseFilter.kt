package cl.clinipets.core.security

import cl.clinipets.identity.application.AuthService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class FirebaseFilter(
    private val authService: AuthService
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(FirebaseFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            try {
                // 1. Verificar token con Firebase
                val decodedToken: FirebaseToken = FirebaseAuth.getInstance().verifyIdToken(token)

                // 2. Delegar sincronización/creación al servicio
                // Esto asegura que el usuario exista en nuestra BD y tenga el rol correcto
                val user = authService.syncFirebaseUser(decodedToken)

                // 3. Construir Principal
                val principal = JwtPayload(
                    userId = user.id!!,
                    email = user.email,
                    role = user.role,
                    expiresAt = Instant.now().plusSeconds(3600) // Validez manejada por Firebase en realidad
                )

                val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                val authentication = UsernamePasswordAuthenticationToken(principal, token, authorities)

                SecurityContextHolder.getContext().authentication = authentication

            } catch (e: Exception) {
                logger.error("[FIREBASE_AUTH] Error validando token: ${e.message}")
                SecurityContextHolder.clearContext()
                // Opcional: response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Token")
                // Pero dejamos que SecurityFilterChain maneje el rechazo si es necesario.
            }
        }
        filterChain.doFilter(request, response)
    }
}
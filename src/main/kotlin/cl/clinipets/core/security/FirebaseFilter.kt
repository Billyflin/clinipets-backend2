package cl.clinipets.core.security

import cl.clinipets.identity.domain.AuthProvider
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
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
    private val userRepository: UserRepository
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
                // Esto lanzará excepción si el token es inválido o expirado
                val decodedToken: FirebaseToken = FirebaseAuth.getInstance().verifyIdToken(token)

                val email = decodedToken.email
                val uid = decodedToken.uid
                val name = decodedToken.name ?: "Usuario App"
                val picture = decodedToken.picture

                if (email != null) {
                    // 2. Sincronizar usuario en DB (Lazy Registration)
                    var user = userRepository.findByEmailIgnoreCase(email)

                    if (user == null) {
                        logger.info("[FIREBASE_AUTH] Usuario nuevo detectado: $email. Creando registro...")
                        val newUser = User(
                            email = email,
                            name = name,
                            passwordHash = "{noop}firebase_uid_$uid",
                            authProvider = AuthProvider.GOOGLE, // Asumimos Google/Firebase por defecto
                            role = UserRole.CLIENT,
                            photoUrl = picture,
                            phoneVerified = true // Firebase verified
                        )
                        user = userRepository.save(newUser)
                    }

                    // 3. Construir Principal compatible con los Controllers existentes
                    val principal = JwtPayload(
                        userId = user!!.id!!,
                        email = user.email,
                        role = user.role,
                        expiresAt = Instant.now().plusSeconds(3600) // Dummy validity
                    )

                    val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                    val authentication = UsernamePasswordAuthenticationToken(principal, token, authorities)

                    SecurityContextHolder.getContext().authentication = authentication
                } else {
                    logger.warn("[FIREBASE_AUTH] Token válido pero sin email. UID: $uid")
                }

            } catch (e: Exception) {
                logger.error("[FIREBASE_AUTH] Error validando token: ${e.message}")
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }
}

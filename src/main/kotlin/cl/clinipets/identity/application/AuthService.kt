package cl.clinipets.identity.application

import cl.clinipets.core.integration.N8nService
import cl.clinipets.core.security.JwtService
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.api.ProfileResponse
import cl.clinipets.identity.api.TokenResponse
import cl.clinipets.identity.api.UserUpdateRequest
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import org.springframework.security.crypto.password.PasswordEncoder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val googleTokenVerifier: GoogleTokenVerifier,
    private val n8nService: N8nService
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional(readOnly = true)
    fun refresh(token: String?): TokenResponse {
        logger.debug("[AUTH_SERVICE] Refresh token start")
        val payload = token?.let { jwtService.parseRefreshToken(it) }
            ?: throw UnauthorizedException("Token de refresh inválido")
        val user = userRepository.findById(payload.userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }
        logger.debug("[AUTH_SERVICE] Refresh token success for user: {}", user.email)
        return issueTokens(user)
    }

    @Transactional(readOnly = true)
    fun me(jwtPayload: JwtPayload): ProfileResponse {
        logger.debug("[AUTH_SERVICE] Obteniendo perfil para: {}", jwtPayload.email)
        val user = userRepository.findById(jwtPayload.userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }
        return ProfileResponse(
            id = user.id!!,
            email = user.email,
            name = user.name,
            role = user.role,
            phone = user.phone,
            address = user.address
        )
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UserUpdateRequest): ProfileResponse {
        logger.info("[AUTH_SERVICE] Actualizando perfil para usuario ID: {}", userId)
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        if (user.name != request.name) {
            if (!n8nService.validarNombre(user.name, request.name)) {
                throw BadRequestException("El nombre ingresado no cumple con las políticas de la comunidad (Validado por IA).")
            }
        }

        user.name = request.name
        user.phone = request.phone
        user.address = request.address

        val updatedUser = userRepository.save(user)
        logger.info("[AUTH_SERVICE] Perfil actualizado para: {}", updatedUser.email)

        return ProfileResponse(
            id = updatedUser.id!!,
            email = updatedUser.email,
            name = updatedUser.name,
            role = updatedUser.role,
            phone = updatedUser.phone,
            address = updatedUser.address
        )
    }

    @Transactional
    fun loginWithGoogle(idToken: String): TokenResponse {
        logger.info("[AUTH_SERVICE] Login con Google iniciado")
        val payload = googleTokenVerifier.verify(idToken)
            ?: throw UnauthorizedException("ID Token inválido")
        val email = payload.email?.lowercase() ?: throw UnauthorizedException("ID Token sin email")
        
        logger.debug("[AUTH_SERVICE] Google Token verificado. Email: {}", email)
        
        val emailVerified = payload.emailVerified
        if (emailVerified != true) {
            logger.warn("[AUTH_SERVICE] Email no verificado en Google: {}", email)
            throw UnauthorizedException("Email no verificado en Google")
        }
        val givenName = payload["given_name"] as? String
        val familyName = payload["family_name"] as? String
        val name = if (!givenName.isNullOrBlank() && !familyName.isNullOrBlank()) {
            "$givenName $familyName"
        } else {
            payload["name"] as? String ?: givenName ?: "Google User"
        }

        // Asignar STAFF si el email coincide con el objetivo, sino CLIENT
        val assignedRole = if (email == "billymartinezc@gmail.com" || email == "andreea.aebc@gmail.com"){
            logger.info("[AUTH_SERVICE] Email coincide con administrador temporal, asignando rol STAFF: {}", email)
            UserRole.STAFF
        } else {
            UserRole.CLIENT
        }

        val user = userRepository.findByEmailIgnoreCase(email) ?: run {
            logger.info("[AUTH_SERVICE] Creando nuevo usuario desde Google: {}", email)
            userRepository.save(
                User(
                    email = email,
                    name = name,
                    passwordHash = passwordEncoder.encode("google-${UUID.randomUUID()}"),
                    role = assignedRole
                )
            )
        }

        // Si el usuario ya existía y debe ser STAFF según el interceptor, actualizar rol si es necesario
        if (user.role != assignedRole && assignedRole == UserRole.STAFF) {
            logger.info("[AUTH_SERVICE] Actualizando rol a STAFF para usuario existente: {}", email)
            // Intentar actualizar y persistir el rol (asumiendo que 'role' es mutable)
            user.role = assignedRole
            userRepository.save(user)
        }

        logger.info("[AUTH_SERVICE] Login exitoso para: {}", user.email)
        return issueTokens(user)
    }

    private fun issueTokens(user: User) = TokenResponse(
        accessToken = jwtService.generateAccessToken(user),
        refreshToken = jwtService.generateRefreshToken(user)
    )
}

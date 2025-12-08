package cl.clinipets.identity.application

import cl.clinipets.core.config.AdminProperties
import cl.clinipets.core.ia.VeterinaryAgentService
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.security.JwtService
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.core.integration.meta.MetaService
import cl.clinipets.identity.api.ProfileResponse
import cl.clinipets.identity.api.TokenResponse
import cl.clinipets.identity.api.UserUpdateRequest
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.identity.domain.AuthProvider
import cl.clinipets.identity.domain.OtpTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val googleTokenVerifier: GoogleTokenVerifier,
    private val veterinaryAgentService: VeterinaryAgentService,
    private val adminProperties: AdminProperties,
    private val otpService: OtpService,
    private val accountMergeService: AccountMergeService,
    private val mascotaRepository: cl.clinipets.veterinaria.domain.MascotaRepository,
    private val otpTokenRepository: OtpTokenRepository,
    private val metaService: MetaService
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val secureRandom = java.security.SecureRandom()

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

        logger.info("[AUTH_SERVICE] Comparando nombres. Actual: '{}', Nuevo: '{}'", user.name, request.name)

        if (user.name != request.name) {
            logger.info("[AUTH_SERVICE] Cambio de nombre detectado. Solicitando validación IA...")

            val esInapropiado = veterinaryAgentService.esNombreInapropiado(request.name)

            logger.info("[AUTH_SERVICE] Resultado Validación IA: Inapropiado = {}", esInapropiado)

            if (esInapropiado) {
                logger.warn("[AUTH_SERVICE] Actualización rechazada por política de nombres. Nombre: {}", request.name)
                throw BadRequestException("El nombre ingresado no cumple con las políticas de la comunidad (Validado por IA).")
            }
        } else {
            logger.info("[AUTH_SERVICE] El nombre no ha cambiado, saltando validación IA.")
        }

        user.name = request.name
        user.phone = request.phone
        user.address = request.address

        val updatedUser = userRepository.save(user)
        logger.info("[AUTH_SERVICE] Perfil actualizado exitosamente para: {}", updatedUser.email)

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
    fun loginWithGoogle(idToken: String, rawPhone: String? = null): TokenResponse {
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
        val assignedRole = if (adminProperties.adminEmails.contains(email)) {
            logger.info("[AUTH_SERVICE] Email coincide con administrador configurado, asignando rol STAFF: {}", email)
            UserRole.STAFF
        } else {
            UserRole.CLIENT
        }

        val normalizedPhone = rawPhone?.let { otpService.normalizePhone(it) }

        val userByEmail = userRepository.findByEmailIgnoreCase(email)
        val userByPhone = normalizedPhone?.let { userRepository.findByPhone(it) }

        val user = when {
            userByEmail != null && userByPhone != null && userByEmail.id != userByPhone.id -> {
                logger.info(
                    "[AUTH_SERVICE] Unificando cuentas Google+Tel. Manteniendo usuario con phone {}",
                    normalizedPhone
                )
                val target = userByPhone.apply {
                    this.email = email
                    this.name = name
                    authProvider = AuthProvider.GOOGLE
                    phoneVerified = true
                    if (role != assignedRole) role = assignedRole
                }
                userRepository.save(target)
                accountMergeService.mergeUsers(userByEmail, target)
            }

            userByEmail != null -> userByEmail
            userByPhone != null -> {
                logger.info("[AUTH_SERVICE] Actualizando cuenta existente por teléfono con email Google {}", email)
                userByPhone.email = email
                userByPhone.name = name
                userByPhone.authProvider = AuthProvider.GOOGLE
                userByPhone.phoneVerified = true
                if (userByPhone.role != assignedRole) userByPhone.role = assignedRole
                userRepository.save(userByPhone)
            }

            else -> {
                logger.info("[AUTH_SERVICE] Creando nuevo usuario desde Google: {}", email)
                userRepository.save(
                    User(
                        email = email,
                        name = name,
                        passwordHash = passwordEncoder.encode("google-${UUID.randomUUID()}"),
                        role = assignedRole,
                        phone = normalizedPhone,
                        authProvider = AuthProvider.GOOGLE,
                        phoneVerified = normalizedPhone != null
                    )
                )
            }
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

    fun requestOtp(phone: String) {
        otpService.requestOtp(phone)
    }

    @Transactional
    fun verifyOtp(phone: String, code: String, name: String? = null): TokenResponse {
        otpService.validateOtp(phone, code)
        val normalizedPhone = otpService.normalizePhone(phone)
        val existing = userRepository.findByPhone(normalizedPhone)

        val user = existing ?: userRepository.save(
            User(
                email = "otp_$normalizedPhone@clinipets.local",
                name = name?.ifBlank { "Cliente Clinipets" } ?: "Cliente Clinipets",
                passwordHash = passwordEncoder.encode("otp-${UUID.randomUUID()}"),
                role = UserRole.CLIENT,
                phone = normalizedPhone,
                phoneVerified = true,
                authProvider = AuthProvider.OTP
            )
        )

        if (user.phone != normalizedPhone) user.phone = normalizedPhone
        user.phoneVerified = true
        user.authProvider = AuthProvider.OTP
        userRepository.save(user)

        logger.info("[AUTH_SERVICE] Login OTP exitoso para {}", normalizedPhone)
        return issueTokens(user)
    }

    fun normalizePhone(phone: String) = otpService.normalizePhone(phone)

    @Transactional
    fun generateOtp(email: String) {
        val user = userRepository.findByEmailIgnoreCase(email.lowercase())
            ?: throw NotFoundException("Usuario no encontrado")

        val phone = user.phone?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("Usuario sin teléfono vinculado. No se puede enviar OTP.")
        val phoneKey = otpService.normalizePhone(phone)

        val last = otpTokenRepository.findTopByPhoneOrderByCreatedAtDesc(phoneKey)
        if (last != null && Duration.between(last.createdAt, Instant.now()) < Duration.ofSeconds(60)) {
            throw BadRequestException("Espera un minuto antes de pedir otro código.")
        }

        val code = String.format("%06d", secureRandom.nextInt(1_000_000))
        otpTokenRepository.deleteByExpiresAtBefore(Instant.now())
        otpTokenRepository.save(
            cl.clinipets.identity.domain.OtpToken(
                phone = phoneKey,
                code = code,
                purpose = "login",
                expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES),
                attempts = 0,
                used = false
            )
        )

        metaService.enviarMensaje(phoneKey, "🔐 Tu código Clinipets es: *$code*")
    }

    @Transactional
    fun validateOtp(email: String, code: String): TokenResponse {
        val user = userRepository.findByEmailIgnoreCase(email.lowercase())
            ?: throw NotFoundException("Usuario no encontrado")
        val phoneKey = user.phone?.takeIf { it.isNotBlank() }?.let { otpService.normalizePhone(it) }
            ?: throw BadRequestException("Usuario sin teléfono vinculado. No se puede validar OTP.")

        val otp = otpTokenRepository.findTopByPhoneOrderByCreatedAtDesc(phoneKey)
            ?: throw UnauthorizedException("Código inválido o expirado.")

        if (otp.used || Instant.now().isAfter(otp.expiresAt)) {
            otpTokenRepository.delete(otp)
            throw UnauthorizedException("Código inválido o expirado.")
        }

        if (otp.attempts >= otp.maxAttempts) {
            otp.used = true
            otpTokenRepository.save(otp)
            throw UnauthorizedException("Código bloqueado por seguridad. Genera uno nuevo.")
        }

        if (otp.code != code) {
            otp.attempts += 1
            otpTokenRepository.save(otp)
            val remaining = (otp.maxAttempts - otp.attempts).coerceAtLeast(0)
            throw BadRequestException("Código incorrecto. Quedan $remaining intentos.")
        }

        otp.used = true
        otpTokenRepository.save(otp)

        return issueTokens(user)
    }

    private fun issueTokens(user: User) = TokenResponse(
        accessToken = jwtService.generateAccessToken(user),
        refreshToken = jwtService.generateRefreshToken(user)
    )
}

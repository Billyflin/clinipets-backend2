package cl.clinipets.identity.application

import cl.clinipets.core.config.AdminProperties
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.api.ProfileResponse
import cl.clinipets.identity.api.UserUpdateRequest
import cl.clinipets.identity.domain.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val adminProperties: AdminProperties,
    private val accountMergeService: AccountMergeService
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    fun syncFirebaseUser(token: FirebaseToken): User {
        val uid = token.uid
        var email = token.email
        var phone = token.claims["phone_number"] as? String
        var name = token.name ?: "Usuario App"
        var picture = token.picture

        // Si el email es nulo en el token, intentamos obtenerlo del perfil de Firebase directamente
        if (email == null) {
            try {
                val fbUser = FirebaseAuth.getInstance().getUser(uid)
                email = fbUser.email
                if (phone == null) phone = fbUser.phoneNumber
                if (name == "Usuario App") name = fbUser.displayName ?: "Usuario App"
                if (picture == null) picture = fbUser.photoUrl
                logger.debug("[AUTH] Email recuperado del Admin SDK: {}", email)
            } catch (e: Exception) {
                logger.warn("[AUTH] No se pudo obtener info adicional de Firebase para UID: {}", uid)
            }
        }

        logger.debug(
            "[AUTH] Sincronizando usuario Firebase. UID: {}, Email: {}, Phone: {}, Name: {}",
            uid,
            email,
            phone,
            name
        )

        // 1. Buscar por Firebase UID
        var user = userRepository.findByFirebaseUid(uid)

        if (user == null) {
            // 2. Si no existe por UID, buscamos por email/phone
            val userByEmail = email?.let { userRepository.findByEmailIgnoreCase(it) }
            val userByPhone = phone?.let { userRepository.findByPhone(it) }

            if (userByEmail != null && userByPhone != null) {
                if (userByEmail.id != userByPhone.id) {
                    logger.info("[AUTH] Fusionando cuenta Teléfono (${userByPhone.id}) en cuenta Email (${userByEmail.id})")
                    user = accountMergeService.mergeUsers(source = userByPhone, target = userByEmail)
                } else {
                    user = userByEmail
                }
            } else if (userByEmail != null) {
                user = userByEmail
            } else if (userByPhone != null) {
                user = userByPhone
            }
        }

        if (user == null) {
            // 3. Crear usuario nuevo
            val finalEmail = email ?: "${(phone ?: uid).replace("+", "").replace(" ", "")}@phone.clinipets.local"
            logger.info("[AUTH] Creando usuario nuevo. UID: {}, Email Final: {}", uid, finalEmail)
            
            val newAuth = if (email != null) AuthProvider.GOOGLE else AuthProvider.PHONE

            user = User(
                firebaseUid = uid,
                email = finalEmail,
                name = name,
                passwordHash = "{noop}firebase_$uid",
                role = if (email != null && adminProperties.adminEmails.contains(email.lowercase())) UserRole.STAFF else UserRole.CLIENT,
                photoUrl = picture,
                phone = phone,
                phoneVerified = true,
                authProvider = newAuth
            )
        } else {
            // 4. Actualizar datos
            user.firebaseUid = uid 

            if (picture != null && user.photoUrl != picture) {
                user.photoUrl = picture
            }

            // Actualizar nombre si el actual es el genérico y el token trae uno real
            if (name != "Usuario App" && (user.name == "Usuario App" || user.name.isBlank())) {
                user.name = name
            }

            val isAdmin = email != null && adminProperties.adminEmails.contains(email.lowercase())
            if (isAdmin && user.role != UserRole.STAFF) {
                user.role = UserRole.STAFF
            }

            if (phone != null && user.phone != phone) {
                user.phone = phone
                user.phoneVerified = true
            }
            
            if (user.authProvider == AuthProvider.OTP || user.authProvider == AuthProvider.WHATSAPP) {
                user.authProvider =
                    if (email != null || token.claims["firebase"]?.let { (it as Map<*, *>)["sign_in_provider"] == "google.com" } == true)
                        AuthProvider.GOOGLE else AuthProvider.PHONE
            }

            // Solo actualizamos el email si recibimos uno real y el actual es un placeholder
            if (email != null && (user.email != email || user.email.endsWith("@phone.clinipets.local"))) {
                logger.info("[AUTH] Actualizando email de {} a {}", user.email, email)
                user.email = email
            }
        }

        return try {
            userRepository.save(user)
        } catch (e: DataIntegrityViolationException) {
            logger.warn("[AUTH] Conflicto de concurrencia detectado para UID: $uid. Re-intentando fetch.")
            userRepository.findByFirebaseUid(uid)
                ?: userRepository.findByEmailIgnoreCase(email ?: "")
                ?: throw e
        }
    }

    @Transactional(readOnly = true)
    fun me(jwtPayload: JwtPayload): ProfileResponse {
        val user = userRepository.findById(jwtPayload.userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }
        return user.toProfileResponse()
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UserUpdateRequest): ProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        user.name = request.name
        user.phone = request.phone
        user.address = request.address

        val updatedUser = userRepository.save(user)
        return updatedUser.toProfileResponse()
    }

    private fun User.toProfileResponse() = ProfileResponse(
        id = this.id!!,
        email = this.email,
        name = this.name,
        role = this.role,
        photoUrl = this.photoUrl,
        phone = this.phone,
        address = this.address,
        phoneVerified = this.phoneVerified
    )
}

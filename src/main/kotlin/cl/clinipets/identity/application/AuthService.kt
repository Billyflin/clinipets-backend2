package cl.clinipets.identity.application

import cl.clinipets.core.config.AdminProperties
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.api.ProfileResponse
import cl.clinipets.identity.api.UserUpdateRequest
import cl.clinipets.identity.domain.*
import com.google.firebase.auth.FirebaseToken
import org.slf4j.LoggerFactory
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
        val email = token.email // Puede ser null si es Phone Auth puro sin email vinculado en Firebase
        val phone = token.claims["phone_number"] as? String
        val name = token.name ?: "Usuario App"
        val picture = token.picture

        // Normalizar teléfono si existe
        val normalizedPhone = phone

        // Determinar si es Admin/Staff por email
        val isAdmin = email != null && adminProperties.adminEmails.contains(email.lowercase())
        val targetRole = if (isAdmin) UserRole.STAFF else UserRole.CLIENT

        // Buscar usuarios existentes
        val userByEmail = email?.let { userRepository.findByEmailIgnoreCase(it) }
        val userByPhone = normalizedPhone?.let { userRepository.findByPhone(it) }

        var user: User? = null

        if (userByEmail != null && userByPhone != null) {
            if (userByEmail.id != userByPhone.id) {
                // Caso de Fusión: Existen dos usuarios distintos.
                // Priorizamos la cuenta con Email (Google) como principal y fusionamos la de teléfono.
                logger.info("[AUTH] Fusionando cuenta Teléfono (${userByPhone.id}) en cuenta Email (${userByEmail.id})")
                user = accountMergeService.mergeUsers(source = userByPhone, target = userByEmail)
            } else {
                // Son el mismo usuario
                user = userByEmail
            }
        } else if (userByEmail != null) {
            user = userByEmail
        } else if (userByPhone != null) {
            user = userByPhone
        }

        if (user == null) {
            logger.info("[AUTH] Creando usuario nuevo. UID: $uid, Email: $email, Phone: $normalizedPhone")
            val finalEmail = email ?: "${normalizedPhone?.replace("+", "")}@phone.clinipets.local"
            val newAuth = if (email != null) AuthProvider.GOOGLE else AuthProvider.PHONE

            user = User(
                email = finalEmail,
                name = name,
                passwordHash = "{noop}firebase_$uid",
                role = targetRole,
                photoUrl = picture,
                phone = normalizedPhone,
                phoneVerified = true,
                authProvider = newAuth
            )
        } else {
            // Actualizar datos
            if (picture != null && user.photoUrl != picture) {
                user.photoUrl = picture
            }
            if (isAdmin && user.role != UserRole.STAFF) {
                user.role = UserRole.STAFF
            }
            if (normalizedPhone != null && user.phone != normalizedPhone) {
                user.phone = normalizedPhone
                user.phoneVerified = true
            }
            
            // Upgrade de Provider / Email
            if (user.authProvider == AuthProvider.OTP || user.authProvider == AuthProvider.WHATSAPP) {
                 user.authProvider = if (email != null) AuthProvider.GOOGLE else AuthProvider.PHONE
            }
            if (email != null && user.email != email) {
                user.email = email
            }
        }
        
        return userRepository.save(user)
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

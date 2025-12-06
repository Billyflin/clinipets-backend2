package cl.clinipets.core.integration

import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationService(
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    fun enviarNotificacion(
        userId: UUID,
        titulo: String,
        cuerpo: String,
        data: Map<String, String> = emptyMap()
    ) {
        if (!firebaseDisponible()) return

        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        enviarMensaje(user, titulo, cuerpo, data)
    }

    fun enviarNotificacionAStaff(
        titulo: String,
        cuerpo: String,
        data: Map<String, String> = emptyMap()
    ) {
        if (!firebaseDisponible()) return

        val staffUsuarios = userRepository.findAllByRoleIn(listOf(UserRole.STAFF, UserRole.ADMIN))
        if (staffUsuarios.isEmpty()) {
            logger.info("[NOTIFS] No se encontraron usuarios STAFF/ADMIN para notificar.")
            return
        }

        staffUsuarios.forEach { enviarMensaje(it, titulo, cuerpo, data) }
    }

    private fun firebaseDisponible(): Boolean {
        if (FirebaseApp.getApps().isEmpty()) {
            logger.warn("[NOTIFS] Firebase no inicializado. Saltando envío.")
            return false
        }
        return true
    }

    private fun enviarMensaje(
        user: User,
        titulo: String,
        cuerpo: String,
        data: Map<String, String>
    ) {
        val token = user.fcmToken
        if (token.isNullOrBlank()) {
            logger.warn("[NOTIFS] Usuario {} sin fcmToken. Notificación descartada.", user.email)
            return
        }

        val message = Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(titulo).setBody(cuerpo).build())
            .putAllData(data)
            .build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("[NOTIFS] Notificación enviada a {}. Response: {}", user.email, response)
        } catch (ex: Exception) {
            logger.error("[NOTIFS] Error al enviar notificación a {}: {}", user.email, ex.message, ex)
        }
    }
}

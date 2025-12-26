package cl.clinipets.core.notifications

import cl.clinipets.identity.domain.UserRepository
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

    /**
     * Envía notificación push a un usuario específico
     */
    fun enviarNotificacion(
        userId: UUID,
        titulo: String,
        cuerpo: String,
        data: Map<String, String> = emptyMap()
    ) {
        try {
            val user = userRepository.findById(userId).orElse(null) ?: run {
                logger.warn("[PUSH] Usuario $userId no encontrado")
                return
            }

            if (user.fcmToken.isNullOrBlank()) {
                logger.debug("[PUSH] Usuario ${user.email} sin token FCM")
                return
            }

            val message = Message.builder()
                .setToken(user.fcmToken)
                .setNotification(
                    Notification.builder()
                        .setTitle(titulo)
                        .setBody(cuerpo)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("[PUSH] Enviada a ${user.email}: $response")
        } catch (ex: Exception) {
            logger.error("[PUSH] Error enviando notificación a $userId", ex)
        }
    }

    /**
     * Envía notificación a múltiples usuarios
     */
    fun enviarNotificacionMasiva(
        userIds: List<UUID>,
        titulo: String,
        cuerpo: String,
        data: Map<String, String> = emptyMap()
    ) {
        logger.info("[PUSH] Enviando notificación masiva a ${userIds.size} usuarios")
        userIds.forEach { userId ->
            enviarNotificacion(userId, titulo, cuerpo, data)
        }
    }
}

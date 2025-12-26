package cl.clinipets.core.notifications

import cl.clinipets.identity.domain.DeviceTokenRepository
import cl.clinipets.identity.domain.UserRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val userRepository: UserRepository,
    private val deviceTokenRepository: DeviceTokenRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * Envía notificación push a todos los dispositivos de un usuario
     */
    @Transactional(readOnly = true)
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

            val tokens = deviceTokenRepository.findAllByUserId(userId)
            if (tokens.isEmpty()) {
                logger.debug("[PUSH] Usuario ${user.email} sin dispositivos registrados")
                return
            }

            logger.info("[PUSH] Enviando a ${user.email} (${tokens.size} dispositivos)")

            tokens.forEach { device ->
                try {
                    val message = Message.builder()
                        .setToken(device.token)
                        .setNotification(
                            Notification.builder()
                                .setTitle(titulo)
                                .setBody(cuerpo)
                                .build()
                        )
                        .putAllData(data)
                        .build()

                    val response = FirebaseMessaging.getInstance().send(message)
                    logger.debug("[PUSH] Enviado OK a token parcial ${device.token.take(8)}...")
                } catch (ex: Exception) {
                    logger.error("[PUSH] Error enviando a un dispositivo de $userId: ${ex.message}")
                    // Opcional: Si el error es "invalid token", eliminarlo de la DB
                }
            }
        } catch (ex: Exception) {
            logger.error("[PUSH] Error general enviando notificación a $userId", ex)
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
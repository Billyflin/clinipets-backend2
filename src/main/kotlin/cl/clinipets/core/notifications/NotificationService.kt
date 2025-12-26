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
    private val channels: List<NotificationChannel>
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * Envía notificación a través de todos los canales disponibles para un usuario
     */
    @Transactional(readOnly = true)
    fun enviarNotificacion(
        userId: UUID,
        titulo: String,
        cuerpo: String,
        data: Map<String, String> = emptyMap()
    ) {
        val user = userRepository.findById(userId).orElse(null) ?: run {
            logger.warn("[NOTIF] Usuario $userId no encontrado")
            return
        }

        logger.info("[NOTIF] Procesando envío para ${user.email} en ${channels.size} canales")

        channels.forEach { channel ->
            try {
                channel.send(userId, user.email, titulo, cuerpo, data)
            } catch (ex: Exception) {
                logger.error("[NOTIF] Error en canal ${channel.getName()} para usuario $userId: ${ex.message}")
            }
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
package cl.clinipets.core.notifications

import cl.clinipets.identity.domain.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class FirebasePushChannel(
    private val deviceTokenRepository: DeviceTokenRepository
) : NotificationChannel {
    private val logger = LoggerFactory.getLogger(FirebasePushChannel::class.java)

    override fun getName(): String = "FIREBASE_PUSH"

    override fun send(userId: UUID, email: String, title: String, body: String, data: Map<String, String>) {
        val tokens = deviceTokenRepository.findAllByUserId(userId)
        if (tokens.isEmpty()) return

        tokens.forEach { device ->
            try {
                val message = Message.builder()
                    .setToken(device.token)
                    .setNotification(
                        Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build()
                    )
                    .putAllData(data)
                    .build()

                FirebaseMessaging.getInstance().send(message)
                logger.debug("[PUSH] Enviado OK a dispositivo de $email")
            } catch (ex: Exception) {
                logger.error("[PUSH] Error enviando a dispositivo de $email: ${ex.message}")
            }
        }
    }
}

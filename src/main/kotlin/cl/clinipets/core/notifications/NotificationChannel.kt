package cl.clinipets.core.notifications

import java.util.*

interface NotificationChannel {
    fun getName(): String
    fun send(userId: UUID, email: String, title: String, body: String, data: Map<String, String>)
}

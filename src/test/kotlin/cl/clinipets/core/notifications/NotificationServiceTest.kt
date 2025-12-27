package cl.clinipets.core.notifications

import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.*

class NotificationServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var channel1: NotificationChannel
    private lateinit var channel2: NotificationChannel
    private lateinit var notificationService: NotificationService

    @BeforeEach
    fun setup() {
        userRepository = mock(UserRepository::class.java)
        channel1 = mock(NotificationChannel::class.java)
        channel2 = mock(NotificationChannel::class.java)

        `when`(channel1.getName()).thenReturn("CHANNEL_1")
        `when`(channel2.getName()).thenReturn("CHANNEL_2")

        notificationService = NotificationService(userRepository, listOf(channel1, channel2))
    }

    @Test
    fun `should send notification through all channels`() {
        val uid = UUID.randomUUID()
        val user = User(id = uid, name = "Test", email = "test@test.com", passwordHash = "", role = UserRole.CLIENT)

        `when`(userRepository.findById(uid)).thenReturn(Optional.of(user))

        notificationService.enviarNotificacion(uid, "Title", "Body")

        verify(channel1).send(eq(uid), eq("test@test.com"), eq("Title"), eq("Body"), any())
        verify(channel2).send(eq(uid), eq("test@test.com"), eq("Title"), eq("Body"), any())
    }

    @Test
    fun `should continue if one channel fails`() {
        val uid = UUID.randomUUID()
        val user = User(id = uid, name = "Test", email = "test@test.com", passwordHash = "", role = UserRole.CLIENT)

        `when`(userRepository.findById(uid)).thenReturn(Optional.of(user))
        `when`(channel1.send(any(), any(), any(), any(), any())).thenThrow(RuntimeException("Fail"))

        notificationService.enviarNotificacion(uid, "Title", "Body")

        verify(channel1).send(any(), any(), any(), any(), any())
        verify(channel2).send(any(), any(), any(), any(), any())
    }
}

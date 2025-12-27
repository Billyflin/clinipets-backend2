package cl.clinipets.identity.application

import cl.clinipets.core.config.AdminProperties
import cl.clinipets.identity.domain.*
import com.google.firebase.auth.FirebaseToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var adminProperties: AdminProperties

    @Mock
    private lateinit var accountMergeService: AccountMergeService

    @Mock
    private lateinit var firebaseToken: FirebaseToken

    private lateinit var authService: AuthService

    @BeforeEach
    fun setup() {
        authService = AuthService(userRepository, adminProperties, accountMergeService)
        lenient().`when`(adminProperties.adminEmails).thenReturn(listOf("admin@clinipets.cl"))
    }

    @Test
    fun `syncFirebaseUser should create new client user if not exists`() {
        // Arrange
        `when`(firebaseToken.uid).thenReturn("fb-uid-123")
        `when`(firebaseToken.email).thenReturn("newuser@test.com")
        `when`(firebaseToken.name).thenReturn("New User")
        `when`(firebaseToken.picture).thenReturn("http://photo.url")
        `when`(userRepository.findByEmailIgnoreCase("newuser@test.com")).thenReturn(null)

        val savedUser = User(
            email = "newuser@test.com", name = "New User", passwordHash = "hash", role = UserRole.CLIENT
        )
        `when`(userRepository.save(any(User::class.java))).thenReturn(savedUser)

        // Act
        val result = authService.syncFirebaseUser(firebaseToken)

        // Assert
        assertEquals("newuser@test.com", result.email)
        assertEquals(UserRole.CLIENT, result.role)
        verify(userRepository).save(argThat { it.email == "newuser@test.com" && it.role == UserRole.CLIENT })
    }

    @Test
    fun `syncFirebaseUser should assign staff role if email is in admin list`() {
        // Arrange
        `when`(firebaseToken.uid).thenReturn("admin-uid")
        `when`(firebaseToken.email).thenReturn("admin@clinipets.cl")
        `when`(userRepository.findByEmailIgnoreCase("admin@clinipets.cl")).thenReturn(null)

        val savedAdmin = User(
            email = "admin@clinipets.cl", name = "Admin", passwordHash = "hash", role = UserRole.STAFF
        )
        `when`(userRepository.save(any(User::class.java))).thenReturn(savedAdmin)

        // Act
        val result = authService.syncFirebaseUser(firebaseToken)

        // Assert
        assertEquals(UserRole.STAFF, result.role)
    }
}

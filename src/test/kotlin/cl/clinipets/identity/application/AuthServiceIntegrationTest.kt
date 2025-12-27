package cl.clinipets.identity.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.identity.domain.UserRepository
import com.google.firebase.auth.FirebaseToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.annotation.Transactional

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AuthServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should sync firebase user into database`() {
        // Arrange
        val mockToken = mock(FirebaseToken::class.java)
        `when`(mockToken.uid).thenReturn("fb-id-unique")
        `when`(mockToken.email).thenReturn("sync@test.com")
        `when`(mockToken.name).thenReturn("Sync User")
        `when`(mockToken.picture).thenReturn("http://image.url")

        // Act
        val syncedUser = authService.syncFirebaseUser(mockToken)

        // Assert
        assertNotNull(syncedUser.id)
        assertEquals("sync@test.com", syncedUser.email)
        assertEquals(UserRole.CLIENT, syncedUser.role)

        val dbUser = userRepository.findByEmailIgnoreCase("sync@test.com")
        assertNotNull(dbUser)
        assertEquals("Sync User", dbUser?.name)
    }
}

package cl.clinipets.backend.nucleo.seguridad

import cl.clinipets.backend.nucleo.api.RefreshTokenService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import java.util.*

class JwtRefreshTokenServiceTest {

    private fun service(days: Long = 30): RefreshTokenService = JwtRefreshTokenService(
        secretB64 = BASE64_SECRET,
        issuer = "test-issuer",
        expirationDays = days,
        cookieName = "clinipets_rft",
        cookieDomain = "",
        cookieSecure = false,
        cookieSameSite = "Lax",
        cookiePath = "/"
    )

    @Test
    fun `issue y parse funcionan en refresh valido`() {
        val svc = service(30)
        val uid = UUID.randomUUID()
        val token = svc.issue(uid, "user@test.cl", listOf("CLIENTE"))
        val payload = svc.parse(token)
        assertEquals(uid, payload.subject)
        assertEquals("user@test.cl", payload.email)
        assertFalse(payload.isExpired)
    }

    @Test
    fun `parse falla con refresh expirado`() {
        val svc = service(days = -1)
        val token = svc.issue(UUID.randomUUID(), "user@test.cl", listOf("CLIENTE"))
        val ex = assertThrows(BadCredentialsException::class.java) {
            svc.parse(token)
        }
        assertTrue(ex.message?.contains("expirado", ignoreCase = true) == true)
    }

    companion object {
        private const val BASE64_SECRET = "VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWZvci1yZWZyZXNoLWp3dC0yNTYtYml0cw=="
    }
}


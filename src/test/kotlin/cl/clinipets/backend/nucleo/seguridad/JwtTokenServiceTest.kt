package cl.clinipets.backend.nucleo.seguridad

import cl.clinipets.backend.nucleo.api.TokenService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import java.util.*

class JwtTokenServiceTest {

    private fun service(expMinutes: Long = 5): TokenService = JwtTokenService(
        secretB64 = BASE64_SECRET,
        issuer = "test-issuer",
        expMinutes = expMinutes
    )

    @Test
    fun `issue y parse funcionan en token valido`() {
        val svc = service(5)
        val uid = UUID.randomUUID()
        val token = svc.issue(uid, "user@test.cl", listOf("CLIENTE"), nombre = "Test", fotoUrl = "http://img")
        val payload = svc.parse(token)
        assertEquals(uid, payload.subject)
        assertEquals("user@test.cl", payload.email)
        assertTrue(payload.roles.contains("CLIENTE"))
        assertFalse(payload.isExpired)
    }

    @Test
    fun `parse falla con token expirado`() {
        val svc = service(expMinutes = -1)
        val token = svc.issue(UUID.randomUUID(), "user@test.cl", listOf("CLIENTE"))
        val ex = assertThrows(BadCredentialsException::class.java) {
            svc.parse(token)
        }
        assertTrue(ex.message?.contains("expirado", ignoreCase = true) == true)
    }

    companion object {
        // 48 bytes base64 (>=256 bits)
        private const val BASE64_SECRET = "VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWZvci1hY2Nlc3MtanNvbi0yNTYtYml0cw=="
    }
}


package cl.clinipets.backend.identidad.web

import cl.clinipets.backend.identidad.aplicacion.AuthService
import cl.clinipets.backend.identidad.aplicacion.AuthService.SessionTokens
import cl.clinipets.backend.identidad.aplicacion.AuthService.UsuarioInfo
import cl.clinipets.backend.nucleo.api.RefreshTokenService
import cl.clinipets.backend.nucleo.api.TokenService
import cl.clinipets.backend.nucleo.seguridad.JwtAuthFilter
import jakarta.servlet.http.HttpServletResponse
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.*

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = [
        "jwt.refresh.cookie-name=clinipets_rft"
    ]
)
@ActiveProfiles("test")
class AuthControllerWebTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var auth: AuthService

    @MockitoBean
    lateinit var refresh: RefreshTokenService

    @MockitoBean
    lateinit var jwtFilter: JwtAuthFilter

    @MockitoBean
    lateinit var tokenService: TokenService

    @Test
    fun `me sin autenticacion retorna authenticated false`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated", equalTo(false)))
    }

    @Test
    fun `refresh entrega nuevo access token cuando cookie es valida`() {
        val uid = UUID.randomUUID()
        val usuario = UsuarioInfo(uid, "user@test.cl", "Nombre", null, listOf("CLIENTE"))
        val now = Instant.now()
        given(auth.refresh("refresh-token")).willReturn(
            SessionTokens(
                accessToken = "new-access",
                refreshToken = "new-refresh",
                accessTokenExpiresAt = now.plusSeconds(60),
                refreshTokenExpiresAt = now.plusSeconds(3600),
                usuario = usuario
            )
        )

        mockMvc.perform(
            post("/api/auth/refresh").cookie(jakarta.servlet.http.Cookie("clinipets_rft", "refresh-token"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token", equalTo("new-access")))
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.usuario.email", equalTo("user@test.cl")))

        verify(refresh).setCookie(any<HttpServletResponse>(), eq("new-refresh"))
    }

    @Test
    fun `logout limpia cookie`() {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok", equalTo(true)))
        verify(refresh).clearCookie(any<HttpServletResponse>())
    }

    @Test
    fun `google login setea refresh y retorna token`() {
        val uid = UUID.randomUUID()
        val usuario = UsuarioInfo(uid, "user@test.cl", "Nombre", null, listOf("CLIENTE"))
        val now = Instant.now()
        given(auth.loginGoogle(AuthService.GoogleLoginRequest("g-token"))).willReturn(
            SessionTokens(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                accessTokenExpiresAt = now.plusSeconds(60),
                refreshTokenExpiresAt = now.plusSeconds(3600),
                usuario = usuario
            )
        )

        mockMvc.perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"g-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token", equalTo("access-token")))
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.usuario.email", equalTo("user@test.cl")))
            .andExpect(jsonPath("$", hasKey("refreshExpiresAt")))

        verify(refresh).setCookie(any<HttpServletResponse>(), eq("refresh-token"))
    }
}

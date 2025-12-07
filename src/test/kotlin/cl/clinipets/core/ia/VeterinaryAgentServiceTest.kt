package cl.clinipets.core.ia

import cl.clinipets.agendamiento.application.DisponibilidadService
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.domain.MascotaRepository
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class VeterinaryAgentServiceTest {

    @Autowired
    private lateinit var agentService: VeterinaryAgentService

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var mascotaRepository: MascotaRepository

    @MockitoBean
    private lateinit var disponibilidadService: DisponibilidadService

    @MockitoBean
    private lateinit var geminiClient: GenAiClientWrapper

    @Test
    fun `procesarMensaje deberia responder correctamente cuando el usuario existe`() {
        // 1. Datos de Prueba
        val telefono = "+56912345678"
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            name = "Test User",
            email = "test@clinipets.cl",
            phone = telefono,
            passwordHash = "dummy",
            role = UserRole.CLIENT
        )
        val respuestaEsperada = "Hola Test User, soy el asistente virtual."

        // 2. Mockear Repositorios
        whenever(userRepository.findByPhone(any())).thenReturn(user)
        whenever(mascotaRepository.findAllByTutorId(userId)).thenReturn(emptyList())

        // 3. Mockear Gemini Client (Wrapper)
        val mockResponse = mock<GenerateContentResponse>()
        whenever(mockResponse.text()).thenReturn(respuestaEsperada)

        // Mockear la llamada del wrapper
        whenever(
            geminiClient.generateContent(
                any<String>(),
                any<Content>(),
                any<GenerateContentConfig>()
            )
        ).thenReturn(mockResponse)

        // 4. Ejecutar
        val respuesta = agentService.procesarMensaje(telefono, "Hola")

        // 5. Verificar
        assertNotNull(respuesta)
        assertEquals(respuestaEsperada, respuesta)
    }
}
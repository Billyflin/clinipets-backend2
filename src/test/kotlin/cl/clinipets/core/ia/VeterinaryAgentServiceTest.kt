package cl.clinipets.core.ia

import cl.clinipets.agendamiento.application.DisponibilidadService
import cl.clinipets.agendamiento.application.ReservaResult
import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    private lateinit var reservaService: ReservaService

    @MockitoBean
    private lateinit var servicioMedicoRepository: ServicioMedicoRepository

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

        val textPart = Part.builder().text(respuestaEsperada).build()
        val candidate = Candidate.builder()
            .content(Content.builder().parts(listOf(textPart)).role("model").build())
            .build()
        whenever(mockResponse.candidates()).thenReturn(Optional.of(listOf(candidate)))
        whenever(mockResponse.text()).thenReturn(respuestaEsperada)

        whenever(
            geminiClient.generateContent(
                any<String>(),
                any<List<Content>>(),
                any<GenerateContentConfig>()
            )
        ).thenReturn(mockResponse)

        // 4. Ejecutar
        val respuesta = agentService.procesarMensaje(telefono, "Hola")

        println("=========================================================================================")
        println("🤖 RESPUESTA DEL AGENTE (MOCK): $respuesta")
        println("=========================================================================================")

        // 5. Verificar
        assertNotNull(respuesta)
        val texto = (respuesta as AgentResponse.Text).content
        assertEquals(respuestaEsperada, texto)
    }

    @Test
    fun `procesarMensaje deberia ejecutar tool consultar_disponibilidad y retornar Lista`() {
        // 1. Datos de Prueba
        val telefono = "+56912345678"
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            name = "Juan Perez",
            email = "juan@test.com",
            phone = telefono,
            passwordHash = "dummy",
            role = UserRole.CLIENT
        )
        val fechaConsulta = LocalDate.now().plusDays(1)
        val fechaStr = fechaConsulta.toString()

        // 2. Mockear Repositorios
        whenever(userRepository.findByPhone(any())).thenReturn(user)
        whenever(mascotaRepository.findAllByTutorId(userId)).thenReturn(emptyList())

        // 3. Mockear Disponibilidad (La Tool llama a esto)
        val slot1 = fechaConsulta.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        val slot2 = fechaConsulta.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant()
        whenever(disponibilidadService.obtenerSlots(eq(fechaConsulta), any())).thenReturn(listOf(slot1, slot2))

        // 4. Configurar Mock de Gemini (Solo Turno 1 -> Function Call)
        val argsMap: Map<String, Any> = mapOf("fecha" to fechaStr)

        val functionCallPart = Part.builder()
            .functionCall(
                FunctionCall.builder()
                    .name("consultar_disponibilidad")
                    .args(argsMap)
                    .build()
            )
            .build()

        val response1 = mock<GenerateContentResponse>()
        val candidate1 = Candidate.builder()
            .content(Content.builder().parts(listOf(functionCallPart)).role("model").build())
            .build()

        whenever(response1.candidates()).thenReturn(Optional.of(listOf(candidate1)))

        whenever(geminiClient.generateContent(any<String>(), any<List<Content>>(), any())).thenReturn(response1)

        // 5. Ejecutar
        val mensajeUsuario = "Quiero agendar para $fechaStr"
        println("Enviando: $mensajeUsuario")
        val respuesta = agentService.procesarMensaje(telefono, mensajeUsuario)

        // 6. Verificar que sea una Lista Interactiva
        println("=========================================================================================")
        println("🤖 RESPUESTA TIPO LISTA: $respuesta")
        println("=========================================================================================")

        assertNotNull(respuesta)
        assert(respuesta is AgentResponse.ListOptions)

        val lista = respuesta as AgentResponse.ListOptions
        assertEquals(2, lista.options.size)
        assertEquals("Ver Horas", lista.buttonLabel)
        assert(lista.text.contains(fechaStr))
    }

    @Test
    fun `procesarMensaje deberia ejecutar tool reservar_cita y retornar Texto con Link`() {
        // 1. Datos de Prueba
        val telefono = "+56912345678"
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            name = "Juan Perez",
            email = "juan@test.com",
            phone = telefono,
            passwordHash = "dummy",
            role = UserRole.CLIENT
        )
        val mascota = Mascota(
            id = UUID.randomUUID(),
            tutor = user,
            nombre = "Firulais",
            especie = Especie.PERRO,
            raza = "Mestizo",
            sexo = Sexo.MACHO,
            pesoActual = BigDecimal.TEN,
            fechaNacimiento = LocalDate.now()
        )
        val servicio = ServicioMedico(
            id = UUID.randomUUID(),
            nombre = "Consulta General",
            precioBase = 10000,
            duracionMinutos = 30,
            categoria = cl.clinipets.servicios.domain.CategoriaServicio.CONSULTA,
            activo = true,
            requierePeso = false
        )
        val linkPago = "http://pago.test/123"
        val fechaHoraStr = "2025-12-08T10:00"

        // 2. Mockear Repositorios
        whenever(userRepository.findByPhone(any())).thenReturn(user)
        whenever(mascotaRepository.findAllByTutorId(userId)).thenReturn(listOf(mascota))
        whenever(servicioMedicoRepository.findByActivoTrue()).thenReturn(listOf(servicio))

        // 3. Mockear ReservaService
        val mockReservaResult = ReservaResult(
            cita = Cita(
                fechaHoraInicio = Instant.now(),
                fechaHoraFin = Instant.now(),
                estado = EstadoCita.PENDIENTE_PAGO,
                precioFinal = 10000,
                tutorId = userId,
                origen = OrigenCita.WHATSAPP
            ),
            paymentUrl = linkPago
        )
        whenever(reservaService.crearReserva(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockReservaResult)

        // 4. Configurar Mock de Gemini (Multi-turno)
        // Turno 1: LLamada a funcion reservar_cita
        val argsMap: Map<String, Any> = mapOf("fechaHora" to fechaHoraStr)
        val functionCallPart = Part.builder()
            .functionCall(
                FunctionCall.builder()
                    .name("reservar_cita")
                    .args(argsMap)
                    .build()
            )
            .build()

        val response1 = mock<GenerateContentResponse>()
        val candidate1 = Candidate.builder()
            .content(Content.builder().parts(listOf(functionCallPart)).role("model").build())
            .build()
        whenever(response1.candidates()).thenReturn(Optional.of(listOf(candidate1)))

        // Turno 2: Respuesta final de texto
        val response2 = mock<GenerateContentResponse>()
        val textoFinal = "Listo, reserva creada. Paga aquí: $linkPago"
        whenever(response2.text()).thenReturn(textoFinal)
        val candidate2 = Candidate.builder()
            .content(Content.builder().parts(listOf(Part.builder().text(textoFinal).build())).role("model").build())
            .build()
        whenever(response2.candidates()).thenReturn(Optional.of(listOf(candidate2)))

        // Secuencia de llamadas al cliente (User input -> History input)
        // any<List<Content>>() is used for simplicity
        whenever(geminiClient.generateContent(any<String>(), any<List<Content>>(), any()))
            .thenReturn(response1)
            .thenReturn(response2)

        // 5. Ejecutar
        val mensajeUsuario = "Confirmo para las 10:00"
        println("Enviando: $mensajeUsuario")
        val respuesta = agentService.procesarMensaje(telefono, mensajeUsuario)

        // 6. Verificar
        println("=========================================================================================")
        println("🤖 RESPUESTA RESERVA: $respuesta")
        println("=========================================================================================")

        assertNotNull(respuesta)
        assert(respuesta is AgentResponse.Text)
        val texto = (respuesta as AgentResponse.Text).content
        assert(texto.contains(linkPago))
    }
}

package cl.clinipets.backend.veterinaria.application

import cl.clinipets.backend.veterinaria.api.MascotaCreateRequest
import cl.clinipets.backend.veterinaria.domain.Especie
import cl.clinipets.backend.veterinaria.domain.MascotaRepository
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=Y29udGFzZWNvbXNlY3JldG9iZGVDbGluSXBldHMxMjM0NTY3OA==",
        "jwt.refresh-secret=cmVmcmVzaFNlY3JldG9iZGVDbGluSXBldHMxMjM0NTY3OA==",
        "jwt.issuer=TestIssuer",
        "google.client-id=test-google"
    ]
)
class MascotaServiceTest(
    @Autowired private val mascotaService: MascotaService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val mascotaRepository: MascotaRepository
) {

    private lateinit var tutorPayload: JwtPayload
    private lateinit var otroPayload: JwtPayload

    @BeforeEach
    fun setup() {
        mascotaRepository.deleteAll()
        userRepository.deleteAll()

        val tutor = userRepository.save(
            User(
                email = "tutor@test.com",
                name = "Tutor",
                passwordHash = "pw",
                role = UserRole.CLIENT
            )
        )
        val otro = userRepository.save(
            User(
                email = "otro@test.com",
                name = "Otro",
                passwordHash = "pw",
                role = UserRole.CLIENT
            )
        )
        tutorPayload = JwtPayload(
            userId = tutor.id!!,
            email = tutor.email,
            role = tutor.role,
            expiresAt = Instant.now()
        )
        otroPayload = JwtPayload(
            userId = otro.id!!,
            email = otro.email,
            role = otro.role,
            expiresAt = Instant.now()
        )
    }

    @Test
    fun `crear y obtener mascota del tutor`() {
        val creada = mascotaService.crear(
            MascotaCreateRequest(
                nombre = "Firulais",
                especie = Especie.PERRO,
                pesoActual = BigDecimal("8.5"),
                fechaNacimiento = LocalDate.of(2022, 5, 10)
            ),
            tutorPayload
        )
        assertEquals("Firulais", creada.nombre)
        assertEquals(tutorPayload.userId, creada.tutorId)

        val listadas = mascotaService.listar(tutorPayload)
        assertEquals(1, listadas.size)
        assertEquals(creada.id, listadas.first().id)
    }

    @Test
    fun `otro usuario no puede acceder mascota ajena`() {
        mascotaService.crear(
            MascotaCreateRequest(
                nombre = "Michi",
                especie = Especie.GATO,
                pesoActual = BigDecimal("4.2"),
                fechaNacimiento = LocalDate.of(2023, 3, 15)
            ),
            tutorPayload
        )
        val listadasPorOtro = mascotaService.listar(otroPayload)
        assertEquals(0, listadasPorOtro.size)
    }
}

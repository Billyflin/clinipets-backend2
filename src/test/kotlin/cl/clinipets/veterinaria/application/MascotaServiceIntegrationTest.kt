package cl.clinipets.veterinaria.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.api.MascotaCreateRequest
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class MascotaServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mascotaService: MascotaService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    private lateinit var owner: User
    private lateinit var otherUser: User
    private lateinit var ownerJwt: JwtPayload
    private lateinit var otherJwt: JwtPayload

    @BeforeEach
    fun setup() {
        val ownerEntity = User(
            name = "Owner", email = "owner@test.com", passwordHash = "hash", role = UserRole.CLIENT
        )
        owner = userRepository.saveAndFlush(ownerEntity)

        val otherEntity = User(
            name = "Other", email = "other@test.com", passwordHash = "hash", role = UserRole.CLIENT
        )
        otherUser = userRepository.saveAndFlush(otherEntity)

        ownerJwt = JwtPayload(owner.id!!, owner.email, owner.role, Instant.now().plusSeconds(3600))
        otherJwt = JwtPayload(otherUser.id!!, otherUser.email, otherUser.role, Instant.now().plusSeconds(3600))
    }

    @Test
    fun `should create and list pets for owner`() {
        val request = MascotaCreateRequest(
            nombre = "Firulais", especie = Especie.PERRO, raza = "Quiltro", sexo = Sexo.MACHO,
            esterilizado = false, fechaNacimiento = LocalDate.of(2020, 1, 1)
        )

        val created = mascotaService.crear(request, ownerJwt)
        assertNotNull(created.id)
        assertEquals("Firulais", created.nombre)

        val list = mascotaService.listar(ownerJwt)
        assertEquals(1, list.size)
        assertEquals("Firulais", list[0].nombre)
    }

    @Test
    fun `should fail when accessing other user pet`() {
        val firulais = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Firulais", especie = Especie.PERRO, raza = "Mix",
                sexo = Sexo.MACHO, fechaNacimiento = LocalDate.now(),
                tutor = owner
            )
        )

        assertThrows(UnauthorizedException::class.java) {
            mascotaService.obtener(firulais.id!!, otherJwt)
        }
    }

    @Test
    fun `staff should be able to access any pet`() {
        val firulais = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Firulais", especie = Especie.PERRO, raza = "Mix",
                sexo = Sexo.MACHO, fechaNacimiento = LocalDate.now(),
                tutor = owner
            )
        )

        val staff = userRepository.saveAndFlush(
            User(
                name = "Staff", email = "staff@test.com", passwordHash = "hash", role = UserRole.STAFF
            )
        )
        val staffJwt = JwtPayload(staff.id!!, staff.email, staff.role, Instant.now().plusSeconds(3600))

        val obtained = mascotaService.obtener(firulais.id!!, staffJwt)
        assertEquals("Firulais", obtained.nombre)
    }
}
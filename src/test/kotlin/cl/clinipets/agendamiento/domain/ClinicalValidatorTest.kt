package cl.clinipets.agendamiento.domain

import cl.clinipets.core.web.BadRequestException
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.*
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class ClinicalValidatorTest {

    private val servicioMedicoRepository = mock<ServicioMedicoRepository>()
    private val validator = ClinicalValidator(servicioMedicoRepository)

    private fun createTutor() = User(
        id = UUID.randomUUID(),
        email = "tutor@test.com",
        name = "Tutor Test",
        passwordHash = "hash",
        role = UserRole.CLIENT
    )

    private fun createMascota(tutor: User, esterilizado: Boolean = false) = Mascota(
        id = UUID.randomUUID(),
        nombre = "Luna",
        especie = Especie.GATO,
        sexo = Sexo.HEMBRA,
        fechaNacimiento = LocalDate.now().minusYears(1),
        tutor = tutor,
        esterilizado = esterilizado
    )

    @Test
    fun `bloquea servicio si la mascota ya esta esterilizada`() {
        val servicio = ServicioMedico(
            nombre = "Esterilización",
            precioBase = BigDecimal.ZERO,
            requierePeso = false,
            duracionMinutos = 60,
            bloqueadoSiEsterilizado = true
        )
        val tutor = createTutor()
        val mascota = createMascota(tutor, true)

        assertThrows<BadRequestException> {
            validator.validarRequisitosClinicos(servicio, mascota, emptySet())
        }
    }

    @Test
    fun `bloquea servicio si falta marcador requerido y no se incluye en el carrito`() {
        val servicio = ServicioMedico(
            nombre = "Cirugía Compleja",
            precioBase = BigDecimal.ZERO,
            requierePeso = false,
            duracionMinutos = 60,
            condicionMarcadorClave = "PREANESTESICO",
            condicionMarcadorValor = "OK"
        )

        val tutor = createTutor()
        val mascota = createMascota(tutor).apply {
            marcadores = mutableMapOf("PREANESTESICO" to "PENDIENTE")
        }

        val servicioRequerido = ServicioMedico(
            id = UUID.randomUUID(),
            nombre = "Perfil Bioquímico",
            precioBase = BigDecimal.ZERO,
            requierePeso = false,
            duracionMinutos = 15,
            actualizaMarcador = "PREANESTESICO"
        )

        whenever(servicioMedicoRepository.findById(servicioRequerido.id!!)).thenReturn(Optional.of(servicioRequerido))

        assertThrows<BadRequestException> {
            validator.validarRequisitosClinicos(servicio, mascota, emptySet())
        }

        validator.validarRequisitosClinicos(servicio, mascota, setOf(servicioRequerido.id!!))
    }
}

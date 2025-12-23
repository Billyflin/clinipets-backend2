package cl.clinipets.backend.agendamiento.domain

import cl.clinipets.agendamiento.domain.ClinicalValidator
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.veterinaria.domain.Temperamento
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class ClinicalValidatorTest {

    private val servicioRepo: ServicioMedicoRepository = mock()
    private val validator = ClinicalValidator(servicioRepo)

    @Test
    fun `lanza error si mascota esterilizada y servicio lo prohíbe`() {
        val servicio = baseServicio().copy(bloqueadoSiEsterilizado = true)
        val mascota = baseMascota().copy(esterilizado = true)

        assertThrows(BadRequestException::class.java) {
            validator.validarRequisitosClinicos(servicio, mascota, emptySet())
        }
    }

    @Test
    fun `lanza error si falta test retroviral para vacuna de leucemia`() {
        val requeridoId = UUID.randomUUID()
        val servicio = baseServicio().copy(serviciosRequeridosIds = mutableSetOf(requeridoId))
        val mascota = baseMascota().copy(testRetroviralNegativo = false)

        val servicioRequerido = baseServicio().copy(id = requeridoId, nombre = "Vacuna Leucemia Felina")
        whenever(servicioRepo.findById(any())).thenReturn(Optional.of(servicioRequerido))

        assertThrows(BadRequestException::class.java) {
            validator.validarRequisitosClinicos(servicio, mascota, emptySet())
        }
    }

    private fun baseServicio(): ServicioMedico = ServicioMedico(
        id = UUID.randomUUID(),
        nombre = "Servicio X",
        precioBase = 1000,
        precioAbono = 200,
        requierePeso = false,
        duracionMinutos = 30,
        activo = true,
        categoria = CategoriaServicio.OTRO,
        especiesPermitidas = mutableSetOf(Especie.PERRO)
    )

    private fun baseMascota(): Mascota = Mascota(
        id = UUID.randomUUID(),
        nombre = "Firulais",
        especie = Especie.PERRO,
        pesoActual = 10.0,
        raza = "Mestizo",
        sexo = Sexo.MACHO,
        esterilizado = false,
        chipIdentificador = null,
        temperamento = Temperamento.DOCIL,
        fechaNacimiento = LocalDate.now().minusYears(2),
        tutor = User(
            id = UUID.randomUUID(),
            email = "test@clinipets.cl",
            name = "Tester",
            passwordHash = "hash",
            role = UserRole.CLIENT
        ),
        testRetroviralNegativo = true,
        fechaUltimoTestRetroviral = null,
        observacionesClinicas = null
    )
}

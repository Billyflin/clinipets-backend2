package cl.clinipets.veterinaria.historial.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.Insumo
import cl.clinipets.servicios.domain.InsumoRepository
import cl.clinipets.servicios.domain.LoteInsumo
import cl.clinipets.servicios.domain.LoteInsumoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.veterinaria.historial.api.FichaCreateRequest
import cl.clinipets.veterinaria.historial.api.ItemPrescripcionRequest
import cl.clinipets.veterinaria.historial.api.RecetaRequest
import cl.clinipets.core.web.ConflictException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.time.LocalDate
import java.util.*

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PrescriptionIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var fichaService: FichaClinicaService

    @Autowired
    private lateinit var insumoRepository: InsumoRepository

    @Autowired
    private lateinit var loteInsumoRepository: LoteInsumoRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testStaff: User
    private lateinit var testMascota: Mascota
    private lateinit var testInsumo: Insumo

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        mascotaRepository.deleteAll()
        insumoRepository.deleteAll()

        val uniqueEmail = "vet-${UUID.randomUUID()}@test.com"
        testStaff = userRepository.saveAndFlush(User(
            email = uniqueEmail, name = "Vet", passwordHash = "hash", role = UserRole.STAFF
        ))

        testMascota = mascotaRepository.saveAndFlush(Mascota(
            nombre = "Bobby", especie = Especie.PERRO, sexo = Sexo.MACHO,
            tutor = testStaff, fechaNacimiento = LocalDate.now().minusYears(1)
        ))

        testInsumo = insumoRepository.saveAndFlush(Insumo(
            nombre = "Antibiótico Peligroso", stockActual = 10.0, stockMinimo = 1.0, unidadMedida = "Frasco",
            contraindicacionMarcador = "ALERGIA_X"
        ))

        loteInsumoRepository.saveAndFlush(LoteInsumo(
            insumo = testInsumo, codigoLote = "LOT-123", fechaVencimiento = LocalDate.now().plusYears(1),
            cantidadInicial = 10.0, cantidadActual = 10.0
        ))
    }

    @Test
    fun `should create prescription and deduct stock`() {
        val request = FichaCreateRequest(
            mascotaId = testMascota.id!!,
            citaId = null,
            fechaAtencion = Instant.now(),
            motivoConsulta = "Consulta por infección",
            pesoActual = 10.0,
            anamnesis = null,
            examenFisico = null,
            avaluoClinico = null,
            planTratamiento = null,
            pesoRegistrado = 10.0,
            temperatura = null,
            frecuenciaCardiaca = null,
            frecuenciaRespiratoria = null,
            observaciones = null,
            esVacuna = false,
            nombreVacuna = null,
            fechaProximaVacuna = null,
            fechaProximoControl = null,
            fechaDesparasitacion = null,
            marcadores = null,
            recetas = listOf(RecetaRequest(
                items = listOf(ItemPrescripcionRequest(
                    insumoId = testInsumo.id!!,
                    dosis = "1 cada 12h",
                    frecuencia = "12h",
                    duracion = "7 dias",
                    cantidadADespachar = 1.0
                ))
            ))
        )

        val response = fichaService.crearFicha(request, testStaff.id!!)

        assertEquals(1, response.recetas.size)
        assertEquals(1, response.recetas[0].items.size)
        assertEquals("Antibiótico Peligroso", response.recetas[0].items[0].nombreInsumo)

        val updatedInsumo = insumoRepository.findById(testInsumo.id!!).get()
        assertEquals(9.0, updatedInsumo.stockActual)
    }

    @Test
    fun `should fail if medication is contraindicated for pet`() {
        // 1. Añadir marcador de alergia a la mascota
        testMascota.marcadores["ALERGIA_X"] = "POSITIVO"
        mascotaRepository.saveAndFlush(testMascota)

        val request = FichaCreateRequest(
            mascotaId = testMascota.id!!,
            citaId = null,
            fechaAtencion = Instant.now(),
            motivoConsulta = "Intento de receta peligrosa",
            pesoActual = 10.0,
            anamnesis = null,
            examenFisico = null,
            avaluoClinico = null,
            planTratamiento = null,
            pesoRegistrado = 10.0,
            temperatura = null,
            frecuenciaCardiaca = null,
            frecuenciaRespiratoria = null,
            observaciones = null,
            esVacuna = false,
            nombreVacuna = null,
            fechaProximaVacuna = null,
            fechaProximoControl = null,
            fechaDesparasitacion = null,
            marcadores = null,
            recetas = listOf(RecetaRequest(
                items = listOf(ItemPrescripcionRequest(
                    insumoId = testInsumo.id!!,
                    dosis = "1ml", frecuencia = "8h", duracion = "3d", cantidadADespachar = 1.0
                ))
            ))
        )

        assertThrows(ConflictException::class.java) {
            fichaService.crearFicha(request, testStaff.id!!)
        }
    }
}

package cl.clinipets.veterinaria.historial.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.agendamiento.domain.*
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.Insumo
import cl.clinipets.servicios.domain.InsumoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import cl.clinipets.veterinaria.historial.api.FichaCreateRequest
import cl.clinipets.veterinaria.historial.api.FichaUpdateRequest
import cl.clinipets.veterinaria.historial.api.ItemPrescripcionRequest
import cl.clinipets.veterinaria.historial.api.RecetaRequest
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Transactional
class FichaClinicaServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var fichaService: FichaClinicaService

    @Autowired
    private lateinit var fichaClinicaRepository: FichaClinicaRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var citaRepository: CitaRepository

    @Autowired
    private lateinit var insumoRepository: InsumoRepository

    @Autowired
    private lateinit var loteInsumoRepository: cl.clinipets.servicios.domain.LoteInsumoRepository

    private lateinit var vet: User
    private lateinit var tutor: User
    private lateinit var mascota: Mascota
    private lateinit var cita: Cita

    @BeforeEach
    fun setup() {
        fichaClinicaRepository.deleteAll()
        citaRepository.deleteAll()
        mascotaRepository.deleteAll()
        loteInsumoRepository.deleteAll()
        insumoRepository.deleteAll()
        userRepository.deleteAll()

        val suffix = UUID.randomUUID().toString().take(8)
        vet = userRepository.saveAndFlush(
            User(
                name = "Vet",
                email = "vet-$suffix@test.com",
                passwordHash = "h",
                role = UserRole.STAFF
            )
        )
        tutor = userRepository.saveAndFlush(
            User(
                name = "Tutor",
                email = "tutor-$suffix@test.com",
                passwordHash = "h",
                role = UserRole.CLIENT
            )
        )
        mascota = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Rex",
                especie = Especie.PERRO,
                sexo = Sexo.MACHO,
                fechaNacimiento = LocalDate.now().minusYears(1),
                tutor = tutor
            )
        )
        cita = citaRepository.saveAndFlush(
            Cita(
                fechaHoraInicio = Instant.now(),
                fechaHoraFin = Instant.now().plusSeconds(1800),
                tutor = tutor,
                estado = EstadoCita.CONFIRMADA,
                precioFinal = BigDecimal("10000"),
                origen = OrigenCita.APP,
                tipoAtencion = TipoAtencion.CLINICA,
                motivoConsulta = "Consulta"
            )
        )
    }

    @Test
    fun `crear ficha clinica con recetas e hitos`() {
        val insumo = insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Antibiotico",
                stockActual = 100.0,
                stockMinimo = 10.0,
                unidadMedida = "ml"
            )
        )
        loteInsumoRepository.saveAndFlush(
            cl.clinipets.servicios.domain.LoteInsumo(
                insumo = insumo,
                codigoLote = "LOT-123",
                fechaVencimiento = LocalDate.now().plusYears(1),
                cantidadInicial = 100.0,
                cantidadActual = 100.0
            )
        )

        val request = FichaCreateRequest(
            mascotaId = mascota.id!!,
            citaId = cita.id!!,
            fechaAtencion = Instant.now(),
            motivoConsulta = "Consulta",
            anamnesis = "Anamnesis",
            pesoActual = 10.5,
            observaciones = "Obs",
            recetas = listOf(
                RecetaRequest(
                    items = listOf(ItemPrescripcionRequest(insumo.id!!, "1ml", "Cada 12h", "7 dias", 14.0)),
                    observaciones = "Nota receta"
                )
            ),
            marcadores = mapOf("alergico" to "penicilina")
        )

        val response = fichaService.crearFicha(request, vet.id!!)

        assertNotNull(response.id)
        assertEquals(1, response.recetas.size)
        assertEquals(1, response.recetas[0].items.size)

        val mascotaActualizada = mascotaRepository.findById(mascota.id!!).get()
        assertEquals("penicilina", mascotaActualizada.marcadores["alergico"])
    }

    @Test
    fun `actualizar ficha clinica`() {
        val requestCrear = FichaCreateRequest(
            mascotaId = mascota.id!!,
            citaId = cita.id!!,
            fechaAtencion = Instant.now(),
            motivoConsulta = "Consulta",
            pesoActual = 10.5
        )
        val creada = fichaService.crearFicha(requestCrear, vet.id!!)

        val update = FichaUpdateRequest(
            anamnesis = "Anamnesis Actualizada",
            avaluoClinico = "Avaluo",
            planTratamiento = "Plan",
            pesoRegistrado = 11.0,
            temperatura = 38.0,
            frecuenciaCardiaca = 80,
            frecuenciaRespiratoria = 20,
            observaciones = "Nuevas obs"
        )

        val actualizada = fichaService.actualizarFicha(creada.id, update)

        assertEquals("Anamnesis Actualizada", actualizada.anamnesis)
        assertEquals(11.0, actualizada.pesoRegistrado)
        assertEquals("Nuevas obs", actualizada.observaciones)
    }

    @Test
    fun `obtener historial de peso`() {
        fichaService.crearFicha(
            FichaCreateRequest(
                mascotaId = mascota.id!!,
                citaId = cita.id!!,
                fechaAtencion = Instant.now().minusSeconds(86400),
                pesoActual = 10.0,
                motivoConsulta = "C1"
            ), vet.id!!
        )

        val cita2 = citaRepository.saveAndFlush(
            Cita(
                fechaHoraInicio = Instant.now(),
                fechaHoraFin = Instant.now().plusSeconds(1800),
                tutor = tutor,
                estado = EstadoCita.CONFIRMADA,
                precioFinal = BigDecimal("10000"),
                origen = OrigenCita.APP,
                tipoAtencion = TipoAtencion.CLINICA,
                motivoConsulta = "Consulta 2"
            )
        )
        fichaService.crearFicha(
            FichaCreateRequest(
                mascotaId = mascota.id!!,
                citaId = cita2.id!!,
                fechaAtencion = Instant.now(),
                pesoActual = 11.0,
                motivoConsulta = "C2"
            ), vet.id!!
        )

        val history = fichaService.obtenerHistorialPeso(mascota.id!!)

        assertEquals(2, history.puntos.size)
        assertTrue(history.puntos.any { it.peso == 10.0 })
        assertTrue(history.puntos.any { it.peso == 11.0 })
    }

    @Test
    fun `obtener ficha por cita`() {
        fichaService.crearFicha(
            FichaCreateRequest(
                mascotaId = mascota.id!!,
                citaId = cita.id!!,
                fechaAtencion = Instant.now(),
                pesoActual = 10.0,
                motivoConsulta = "Test"
            ), vet.id!!
        )

        val response = fichaService.obtenerFichaPorCita(cita.id!!)

        assertNotNull(response)
        assertEquals(cita.id, response.citaId)
    }

    @Test
    fun `actualizar ficha inexistente lanza error`() {
        assertThrows(NotFoundException::class.java) {
            fichaService.actualizarFicha(UUID.randomUUID(), FichaUpdateRequest(anamnesis = "Fail"))
        }
    }
}

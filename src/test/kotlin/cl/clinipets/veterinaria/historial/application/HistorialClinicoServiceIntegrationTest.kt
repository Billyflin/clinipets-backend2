package cl.clinipets.veterinaria.historial.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.domain.*
import cl.clinipets.veterinaria.historial.domain.FichaClinica
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import cl.clinipets.veterinaria.historial.domain.SignosVitalesData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Transactional
class HistorialClinicoServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var historialService: HistorialClinicoService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mascotaRepository: MascotaRepository

    @Autowired
    private lateinit var fichaClinicaRepository: FichaClinicaRepository

    @Autowired
    private lateinit var planPreventivoRepository: PlanPreventivoRepository

    @Autowired
    private lateinit var signosVitalesRepository: SignosVitalesRepository

    @Test
    fun `should return full medical history`() {
        // Setup
        val tutor = userRepository.saveAndFlush(
            User(
                name = "Tutor Historial",
                email = "tutor_historial@test.com",
                passwordHash = "hash",
                role = UserRole.CLIENT
            )
        )

        val vet = userRepository.saveAndFlush(
            User(
                name = "Vet 1", email = "vet_historial@test.com", passwordHash = "hash", role = UserRole.STAFF
            )
        )

        val mascota = mascotaRepository.saveAndFlush(
            Mascota(
                nombre = "Bobby Historial", especie = Especie.PERRO, sexo = Sexo.MACHO,
                fechaNacimiento = LocalDate.now().minusYears(2), tutor = tutor
            )
        )

        // 1. Add Ficha Clinica
        fichaClinicaRepository.saveAndFlush(
            FichaClinica(
                mascota = mascota,
                motivoConsulta = "Control preventivo",
                avaluoClinico = "Sano",
                autor = vet,
                signosVitales = SignosVitalesData(pesoRegistrado = 15.5, temperatura = 38.5)
            )
        )

        // 2. Add Plan Preventivo
        planPreventivoRepository.saveAndFlush(
            PlanPreventivo(
                mascota = mascota,
                tipo = TipoPreventivo.VACUNA,
                producto = "Sextuple",
                fechaAplicacion = Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS),
                fechaRefuerzo = Instant.now().plus(350, java.time.temporal.ChronoUnit.DAYS)
            )
        )

        // 3. Add independent Vital Signs
        signosVitalesRepository.saveAndFlush(
            SignosVitales(
                mascota = mascota,
                peso = 15.7,
                temperatura = 38.6,
                frecuenciaCardiaca = 80
            )
        )

        val jwt = JwtPayload(tutor.id!!, tutor.email, tutor.role, Instant.now().plusSeconds(3600))

        // Test
        val response = historialService.obtenerHistorialCompleto(mascota.id!!, jwt)

        assertEquals(mascota.id, response.mascotaId)
        assertEquals(1, response.fichasClinicas.size)
        assertNotNull(response.planPreventivo.ultimaVacuna)
        assertEquals("Sextuple", response.planPreventivo.ultimaVacuna?.nombre)
        assertEquals(15.7, response.signosVitales.ultimoRegistro?.peso)
    }
}

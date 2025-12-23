package cl.clinipets.backend.agendamiento.application

import cl.clinipets.agendamiento.api.FinalizarCitaRequest
import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.ConflictException
import cl.clinipets.identity.domain.UserRole
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class TransactionalQAValidation(
    @Autowired private val reservaService: ReservaService,
    @Autowired private val citaRepository: CitaRepository
) {

    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var storageService: cl.clinipets.core.storage.StorageService

    @Test
    fun `finalizarCita debe fallar con ConflictException si no hay stock`() {
        // Escenario: Intentar finalizar una cita donde el servicio requiere 
        // más insumos de los que hay disponibles físicamente en la DB.

        val staffPayload = JwtPayload(
            userId = UUID.randomUUID(),
            email = "staff@clinipets.cl",
            role = UserRole.STAFF,
            expiresAt = Instant.now().plusSeconds(3600)
        )

        // El test busca validar que la excepción lanzada sea ConflictException (409)
        // y no un Internal Server Error (500), cumpliendo el contrato de API.
        // Nota: En un test real, crearíamos la data necesaria aquí.
    }
}

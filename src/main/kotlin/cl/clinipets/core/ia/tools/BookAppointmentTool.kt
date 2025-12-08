package cl.clinipets.core.ia.tools

import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Component
class BookAppointmentTool(
    private val mascotaRepository: MascotaRepository,
    private val reservaService: ReservaService,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val userRepository: UserRepository,
    private val clinicZoneId: ZoneId
) : AgentTool {

    private val logger = LoggerFactory.getLogger(BookAppointmentTool::class.java)

    override val name: String = "reservar_cita"
    override val description: String = "Confirma reserva. Requiere fechaHora ISO y servicio."

    override fun getFunctionDeclaration(): FunctionDeclaration {
        val propString = Schema.builder()
            .type(Type.Known.STRING)
            .build()

        return FunctionDeclaration.builder()
            .name(name)
            .description(description)
            .parameters(
                Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(mapOf("fechaHora" to propString, "servicio" to propString))
                    .required(listOf("fechaHora", "servicio"))
                    .build()
            )
            .build()
    }

    override fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult {
        val argsMap = args ?: emptyMap()
        val fechaHoraStr = argsMap["fechaHora"] as? String
        val servicioStr = argsMap["servicio"] as? String
        val userDb = resolverTutor(context) ?: return ToolExecutionResult("Error: No registrado o no identificado.")

        if (fechaHoraStr == null) return ToolExecutionResult("Error: Fecha y hora requerida.")

        val (responseText, paymentUrl) = try {
            val fechaHora = LocalDateTime.parse(fechaHoraStr)
            val fechaHoraInstant = fechaHora.atZone(clinicZoneId).toInstant()

            val mascotas = mascotaRepository.findAllByTutorId(userDb.id!!)
            if (mascotas.isEmpty()) {
                return ToolExecutionResult("Error: No tienes mascotas registradas. Debes registrar una mascota antes de agendar.")
            }

            val servicioDb = servicioStr?.let { buscarServicio(it) }
            if (servicioDb == null) {
                return ToolExecutionResult("Error: No se encontró el servicio médico solicitado.")
            }

            val mascota = mascotas.first()
            val detalles = listOf(DetalleReservaRequest(servicioId = servicioDb.id!!, mascotaId = mascota.id))
            val tutorPayload = JwtPayload(
                userId = userDb.id!!,
                email = userDb.email,
                role = userDb.role,
                expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            )

            val resultado = reservaService.crearReserva(
                detallesRequest = detalles,
                fechaHoraInicio = fechaHoraInstant,
                origen = OrigenCita.WHATSAPP,
                tutor = tutorPayload
            )
            logger.warn("[DEBUG_PAGO] Reserva creada. Resultado PaymentURL: {}", resultado.paymentUrl)
            "EXITO: Reserva creada. Dile al usuario que le enviaremos el link de pago en un mensaje separado." to resultado.paymentUrl
        } catch (e: Exception) {
            logger.error("Error creando reserva", e)
            "Ocurrió un error al intentar crear la reserva: ${e.message}" to null
        }
        return ToolExecutionResult(responseText, paymentUrl)
    }

    private fun resolverTutor(context: ToolContext): User? {
        if (context.userId != null) {
            return userRepository.findById(context.userId).orElse(null)
        }
        val telefono = context.userPhone
        return userRepository.findByPhone(telefono)
            ?: userRepository.findByPhone(telefono.removePrefix("56"))
            ?: userRepository.findByPhone("+" + telefono)
    }

    private fun buscarServicio(input: String): ServicioMedico? {
        val todos = servicioMedicoRepository.findByActivoTrue()
        return todos.find { it.nombre.equals(input, ignoreCase = true) }
            ?: todos.find { it.nombre.contains(input, ignoreCase = true) }
            ?: todos.find { it.nombre.equals("Consulta General", ignoreCase = true) }
    }
}

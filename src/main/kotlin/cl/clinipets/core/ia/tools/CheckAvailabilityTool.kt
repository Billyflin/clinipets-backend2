package cl.clinipets.core.ia.tools

import cl.clinipets.agendamiento.application.DisponibilidadService
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class CheckAvailabilityTool(
    private val disponibilidadService: DisponibilidadService,
    private val servicioMedicoRepository: ServicioMedicoRepository
) : AgentTool {

    override val name: String = "consultar_disponibilidad"
    override val description: String = "Consulta disponibilidad. Requiere fecha y tipo de servicio."

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
                    .properties(mapOf("fecha" to propString, "servicio" to propString))
                    .required(listOf("fecha", "servicio"))
                    .build()
            )
            .build()
    }

    override fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult {
        val argsMap = args ?: emptyMap()
        val fechaStr = argsMap["fecha"] as? String ?: return ToolExecutionResult("Error: Fecha requerida.")
        val servicioStr = argsMap["servicio"] as? String

        val responseText = try {
            val fecha = LocalDate.parse(fechaStr)

            val servicioDb = servicioStr?.let { buscarServicio(it) }
            val duracion = servicioDb?.duracionMinutos ?: 30
            val nombreServicio = servicioDb?.nombre ?: "Consulta General"
            val precioTotal = servicioDb?.precioBase ?: 0
            val precioAbono = servicioDb?.precioAbono ?: 0

            val slots = disponibilidadService.obtenerSlots(fecha, duracion)
            if (slots.isEmpty()) {
                "No quedan horas disponibles para $nombreServicio el $fechaStr."
            } else {
                "Encontré ${slots.size} horas para $nombreServicio (Duración: $duracion min). Valor Total: $$precioTotal. REGLA DE PAGO: Para agendar, el cliente DEBE pagar un abono online de $$precioAbono ahora mismo. El saldo se paga en clínica."
            }
        } catch (e: Exception) {
            "Fecha inválida o error en agenda."
        }
        return ToolExecutionResult(responseText)
    }

    private fun buscarServicio(input: String): ServicioMedico? {
        val todos = servicioMedicoRepository.findByActivoTrue()
        return todos.find { it.nombre.equals(input, ignoreCase = true) }
            ?: todos.find { it.nombre.contains(input, ignoreCase = true) }
            ?: todos.find { it.nombre.equals("Consulta General", ignoreCase = true) }
    }
}

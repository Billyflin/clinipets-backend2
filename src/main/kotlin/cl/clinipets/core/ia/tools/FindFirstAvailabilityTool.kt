package cl.clinipets.core.ia.tools

import cl.clinipets.agendamiento.application.DisponibilidadService
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Component
class FindFirstAvailabilityTool(
    private val disponibilidadService: DisponibilidadService,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val clinicZoneId: ZoneId
) : AgentTool {

    override val name: String = "buscar_primera_disponibilidad"
    override val description: String = "Busca la primera fecha con horas disponibles para un servicio."

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
                    .properties(mapOf("servicio" to propString))
                    .required(listOf("servicio"))
                    .build()
            )
            .build()
    }

    override fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult {
        val argsMap = args ?: emptyMap()
        val servicioStr = argsMap["servicio"] as? String ?: return ToolExecutionResult("Error: Servicio requerido.")
        val primera = buscarPrimeraDisponibilidad(servicioStr)
            ?: return ToolExecutionResult("No encontré horas cercanas para ese servicio.")
        val servicioDb = buscarServicio(servicioStr)
        val nombreServicio = servicioDb?.nombre ?: servicioStr
        val duracion = servicioDb?.duracionMinutos ?: 30
        return ToolExecutionResult("Tengo la fecha más cercana para $nombreServicio (duración $duracion min) el ${primera.first}. ¿Quieres ver los horarios?")
    }

    private fun buscarPrimeraDisponibilidad(servicioStr: String): Pair<LocalDate, List<Instant>>? {
        val servicioDb = buscarServicio(servicioStr)
        val duracion = servicioDb?.duracionMinutos ?: 30
        val maxDiasBusqueda = 30
        val hoy = LocalDate.now(clinicZoneId)

        for (i in 0..maxDiasBusqueda) {
            val fecha = hoy.plusDays(i.toLong())
            val slots = disponibilidadService.obtenerSlots(fecha, duracion)
            if (slots.isNotEmpty()) {
                return fecha to slots
            }
        }
        return null
    }

    private fun buscarServicio(input: String): ServicioMedico? {
        val todos = servicioMedicoRepository.findByActivoTrue()
        return todos.find { it.nombre.equals(input, ignoreCase = true) }
            ?: todos.find { it.nombre.contains(input, ignoreCase = true) }
            ?: todos.find { it.nombre.equals("Consulta General", ignoreCase = true) }
    }
}

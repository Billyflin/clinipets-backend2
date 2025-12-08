package cl.clinipets.core.ia.tools

import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RegisterClientTool(
    private val userRepository: UserRepository
) : AgentTool {

    private val logger = LoggerFactory.getLogger(RegisterClientTool::class.java)

    override val name: String = "registrar_cliente"
    override val description: String = "Registra un nuevo cliente solo con su nombre."

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
                    .properties(mapOf("nombre" to propString))
                    .required(listOf("nombre"))
                    .build()
            )
            .build()
    }

    override fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult {
        val argsMap = args ?: emptyMap()
        var nombre = argsMap["nombre"] as? String
        if (nombre == null) return ToolExecutionResult("Error: Nombre requerido.")

        nombre = nombre.replace(Regex("(?i)^(hola|soy|me llamo|mi nombre es)\\s+"), "").trim()

        val responseText = try {
            val phoneClean = context.userPhone.replace("+", "")
            val emailGen = "wsp_$phoneClean@clinipets.local"

            if (userRepository.existsByEmailIgnoreCase(emailGen)) {
                "El usuario ya estaba registrado."
            } else {
                val newUser = User(
                    name = nombre,
                    email = emailGen,
                    phone = context.userPhone,
                    passwordHash = "wsp_auto_generated",
                    role = UserRole.CLIENT
                )
                userRepository.save(newUser)
                "¡Registrada como $nombre! Ahora puedes agendar."
            }
        } catch (e: Exception) {
            logger.error("Error registrando cliente", e)
            "Error al registrar cliente."
        }
        return ToolExecutionResult(responseText)
    }
}

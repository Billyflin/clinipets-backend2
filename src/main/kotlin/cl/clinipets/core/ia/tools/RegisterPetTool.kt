package cl.clinipets.core.ia.tools

import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class RegisterPetTool(
    private val mascotaRepository: MascotaRepository,
    private val userRepository: UserRepository
) : AgentTool {

    private val logger = LoggerFactory.getLogger(RegisterPetTool::class.java)

    override val name: String = "registrar_mascota"
    override val description: String = "Registra una nueva mascota para el usuario actual."

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
                    .properties(
                        mapOf(
                            "nombre" to propString,
                            "especie" to propString,
                            "raza" to propString
                        )
                    )
                    .required(listOf("nombre", "especie"))
                    .build()
            )
            .build()
    }

    override fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult {
        val argsMap = args ?: emptyMap()
        val nombre = (argsMap["nombre"] as? String)?.trim()
        val especieInput = (argsMap["especie"] as? String)?.trim()
        val raza = (argsMap["raza"] as? String)?.trim()

        val userDb = resolverTutor(context) ?: return ToolExecutionResult("Error: No registrado o no identificado.")
        if (nombre.isNullOrBlank() || especieInput.isNullOrBlank()) return ToolExecutionResult("Error: Nombre y especie son obligatorios.")

        val responseText = try {
            val especie =
                if (especieInput.lowercase().contains("gato") || especieInput.lowercase().contains("felino")) {
                    Especie.GATO
                } else {
                    Especie.PERRO
                }

            val nuevaMascota = Mascota(
                nombre = nombre,
                especie = especie,
                pesoActual = BigDecimal("10.0"),
                raza = raza?.takeIf { it.isNotBlank() } ?: "Mestizo",
                sexo = Sexo.MACHO,
                fechaNacimiento = LocalDate.now(),
                tutor = userDb
            )
            mascotaRepository.save(nuevaMascota)
            "¡Listo! Agregué a $nombre ($especie) a tu perfil. ¿Quieres agendar hora para él/ella?"
        } catch (e: Exception) {
            logger.error("Error registrando mascota", e)
            "Error al registrar la mascota."
        }

        return ToolExecutionResult(responseText)
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
}

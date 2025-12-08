package cl.clinipets.core.ia.tools

import cl.clinipets.identity.application.OtpService
import cl.clinipets.identity.domain.UserRepository
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.springframework.stereotype.Component

@Component
class SendOtpTool(
    private val otpService: OtpService
) : AgentTool {

    override val name: String = "enviar_otp"
    override val description: String = "Envía un código OTP al teléfono del chat actual."

    override fun getFunctionDeclaration(): FunctionDeclaration {
        return FunctionDeclaration.builder()
            .name(name)
            .description(description)
            .parameters(
                Schema.builder()
                    .type(Type.Known.OBJECT)
                    .build()
            )
            .build()
    }

    override fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult {
        otpService.requestOtp(context.userPhone)
        return ToolExecutionResult("Te envié un código de verificación a este número. Respóndeme con ese código de 6 dígitos.")
    }
}

@Component
class ValidateOtpTool(
    private val otpService: OtpService,
    private val userRepository: UserRepository
) : AgentTool {

    override val name: String = "validar_otp"
    override val description: String = "Valida el código OTP enviado al teléfono."

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
                    .properties(mapOf("code" to propString))
                    .required(listOf("code"))
                    .build()
            )
            .build()
    }

    override fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult {
        val argsMap = args ?: emptyMap()
        val code = argsMap["code"] as? String ?: return ToolExecutionResult("Error: Código requerido.")

        try {
            otpService.validateOtp(context.userPhone, code)
            marcarTelefonoVerificado(context.userPhone)
            return ToolExecutionResult("Código verificado. Quedaste autenticado con tu teléfono.")
        } catch (e: Exception) {
            return ToolExecutionResult("Error al validar código: ${e.message}")
        }
    }

    private fun marcarTelefonoVerificado(telefono: String) {
        val normalized = otpService.normalizePhone(telefono)
        userRepository.findByPhone(normalized)?.let {
            it.phoneVerified = true
            userRepository.save(it)
        }
    }
}

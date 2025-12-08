package cl.clinipets.core.ia.tools

import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Part
import java.util.UUID

/**
 * Context provided to the tool during execution.
 */
data class ToolContext(
    val userPhone: String,
    val userId: UUID?
)

/**
 * Result of a tool execution.
 */
data class ToolExecutionResult(
    val responseText: String,
    val paymentUrl: String? = null
) {
    /**
     * Converts the result text to a Gemini FunctionResponse Part.
     */
    fun toPart(functionName: String): Part {
        return Part.builder()
            .functionResponse(
                com.google.genai.types.FunctionResponse.builder()
                    .name(functionName)
                    .response(mapOf("result" to responseText))
                    .build()
            )
            .build()
    }
}

/**
 * Strategy interface for AI Agent tools.
 */
interface AgentTool {
    val name: String
    val description: String

    /**
     * Returns the function declaration for the Gemini API.
     */
    fun getFunctionDeclaration(): FunctionDeclaration

    /**
     * Executes the tool logic.
     * @param args The arguments provided by the model.
     * @param context The context regarding the current user/session.
     */
    fun execute(args: Map<String, Any?>?, context: ToolContext): ToolExecutionResult
}
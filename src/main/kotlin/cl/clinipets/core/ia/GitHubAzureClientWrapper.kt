package cl.clinipets.core.ia

import com.azure.ai.inference.ChatCompletionsClient
import com.azure.ai.inference.models.ChatCompletionsOptions
import com.azure.ai.inference.models.ChatRequestMessage
import com.azure.ai.inference.models.ChatRequestSystemMessage
import com.azure.ai.inference.models.ChatRequestUserMessage
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import org.slf4j.LoggerFactory

/**
 * Wrapper GitHub/ Azure Inference compatible con GenAiClientWrapper.
 */
class GitHubAzureClientWrapper(
    private val client: ChatCompletionsClient,
    private val model: String,
    private val temperature: Double,
    private val maxOutputTokens: Int
) : GenAiClientWrapper {

    private val logger = LoggerFactory.getLogger(GitHubAzureClientWrapper::class.java)

    override fun generateContent(
        model: String,
        content: Content,
        config: GenerateContentConfig?
    ): GenerateContentResponse {
        return generateContent(model, listOf(content), config)
    }

    override fun generateContent(
        model: String,
        history: List<Content>,
        config: GenerateContentConfig?
    ): GenerateContentResponse {
        val messages = history.map { content ->
            val role = content.role().orElse("user")
            val text = sanitizeText(
                content.parts().orElse(emptyList()).joinToString(" ") { it.text().orElse("") }
            )
            when (role.lowercase()) {
                "system" -> ChatRequestSystemMessage(text) as ChatRequestMessage
                else -> ChatRequestUserMessage(text) as ChatRequestMessage
            }
        }
        val options = ChatCompletionsOptions(messages).setModel(this.model)
        options.temperature = temperature
        options.maxTokens = maxOutputTokens

        logger.info(
            "[IA_HTTP] GitHub AI via Azure SDK. model={} msgs={} temp={} maxTokens={}",
            this.model,
            messages.size,
            temperature,
            maxOutputTokens
        )

        val completions = client.complete(options)
        val content = completions.choices?.firstOrNull()?.message?.content ?: ""
        logger.info(
            "[IA_HTTP] GitHub AI response. choices={} contentLen={} preview='{}'",
            completions.choices?.size ?: 0,
            content.length,
            content.take(200)
        )
        val part = Part.builder().text(content).build()
        val respContent = Content.builder().role("model").parts(listOf(part)).build()
        val candidate = Candidate.builder().content(respContent).build()
        return GenerateContentResponse.builder().candidates(listOf(candidate)).build()
    }

    private fun sanitizeText(raw: String): String {
        // Azure SDK 1.0.0-beta.1 se queja con caracteres de control sin escapar; normalizamos.
        return raw
            .replace("\r", "")
            .replace("\n", "\\n")
            .replace(Regex("[\\u0000-\\u001F&&[^\\n]]"), " ")
            .trim()
    }
}

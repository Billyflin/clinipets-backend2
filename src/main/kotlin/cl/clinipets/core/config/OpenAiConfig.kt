package cl.clinipets.core.config

import cl.clinipets.core.ia.GenAiClientWrapper
import cl.clinipets.core.ia.GitHubAzureClientWrapper
import com.azure.ai.inference.ChatCompletionsClientBuilder
import com.azure.core.credential.AzureKeyCredential
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["ia.enabled"], havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(name = ["ia.provider"], havingValue = "openai", matchIfMissing = false)
class OpenAiConfig {

    @Bean
    fun openAiClientWrapper(
        @Value("\${oa.api-key}") apiKey: String,
        @Value("\${oa.model}") model: String,
        @Value("\${oa.base-url:https://api.openai.com/v1}") baseUrl: String,
        @Value("\${oa.max-output-tokens:256}") maxOutputTokens: Int,
        @Value("\${oa.temperature:0.3}") temperature: Double
    ): GenAiClientWrapper {
        val client = ChatCompletionsClientBuilder()
            .credential(AzureKeyCredential(apiKey))
            .endpoint(baseUrl)
            .buildClient()

        return GitHubAzureClientWrapper(
            client = client,
            model = model,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens
        )
    }
}

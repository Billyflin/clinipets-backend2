package cl.clinipets.core.config

import cl.clinipets.core.ia.GenAiClientWrapper
import cl.clinipets.core.ia.GitHubAzureClientWrapper
import com.azure.ai.inference.ChatCompletionsClientBuilder
import com.azure.ai.inference.models.ChatCompletionsOptions
import com.azure.ai.inference.models.ChatRequestSystemMessage
import com.azure.ai.inference.models.ChatRequestUserMessage
import com.azure.core.credential.AzureKeyCredential
import com.azure.core.http.HttpHeaders
import com.azure.core.http.policy.AddHeadersPolicy
import com.azure.core.http.okhttp.OkHttpAsyncHttpClientBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["ia.enabled"], havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(name = ["ia.provider"], havingValue = "github", matchIfMissing = false)
class GitHubAiConfig {

    private val logger = LoggerFactory.getLogger(GitHubAiConfig::class.java)

    @Bean
    fun gitHubClientWrapper(
        @Value("\${gh.api-key}") apiKey: String,
        @Value("\${gh.model}") model: String,
        @Value("\${gh.base-url:https://models.github.ai/inference}") baseUrl: String,
        @Value("\${gh.max-output-tokens:256}") maxOutputTokens: Int,
        @Value("\${gh.temperature:0.3}") temperature: Double
    ): GenAiClientWrapper {
        logger.info(
            "[IA_CONFIG] Inicializando cliente GitHub AI. endpoint={} model={} token='{}'",
            baseUrl,
            model,
            apiKey
        )

        val client = buildGitHubAzureClient(apiKey, baseUrl)

        return GitHubAzureClientWrapper(
            client = client,
            model = model,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens
        )
    }

    @Bean
    fun githubStartupProbe(
        @Value("\${gh.api-key}") apiKey: String,
        @Value("\${gh.model}") model: String,
        @Value("\${gh.base-url:https://models.github.ai/inference}") baseUrl: String,
        @Value("\${ia.startup-probe.enabled:true}") probeEnabled: Boolean,
        client: GenAiClientWrapper
    ): ApplicationRunner = ApplicationRunner {
        if (!probeEnabled) return@ApplicationRunner
        if (apiKey.isBlank()) {
            logger.warn("[IA_PROBE] Saltando prueba de GitHub AI porque falta GITHUB_TOKEN")
            return@ApplicationRunner
        }
        try {
            logger.info("[IA_PROBE] Usando token GITHUB_TOKEN='{}'", apiKey)

            val azureClient = buildGitHubAzureClient(apiKey, baseUrl)

            val messages = listOf(
                ChatRequestSystemMessage(""),
                ChatRequestUserMessage("ping")
            )

            val options = ChatCompletionsOptions(messages).apply {
                setModel(model)
            }

            val response = azureClient.complete(options)
            val text = response.choices?.firstOrNull()?.message?.content ?: "(sin texto)"
            logger.info("[IA_PROBE] GitHub AI OK. Modelo={} Respuesta='{}'", model, text)
        } catch (ex: Exception) {
            logger.warn("[IA_PROBE] GitHub AI fallo en prueba de arranque: {}", ex.toString())
        }
    }

    private fun buildGitHubAzureClient(apiKey: String, baseUrl: String) = ChatCompletionsClientBuilder()
        // GitHub Models espera Authorization: Bearer <token>
        .addPolicy(AddHeadersPolicy(HttpHeaders().set("Authorization", "Bearer $apiKey")))
        // Algunos SDKs usan api-key; mantenemos también este header por compatibilidad
        .credential(AzureKeyCredential(apiKey))
        // Evitamos Netty y usamos OkHttp para el cliente HTTP subyacente
        .httpClient(OkHttpAsyncHttpClientBuilder().build())
        .endpoint(baseUrl)
        .buildClient()
}

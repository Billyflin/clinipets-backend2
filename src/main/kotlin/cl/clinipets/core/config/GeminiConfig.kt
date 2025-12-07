package cl.clinipets.core.config

import cl.clinipets.core.ia.GenAiClientWrapper
import cl.clinipets.core.ia.GenAiClientWrapperImpl
import com.google.genai.Client
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GeminiConfig {

    @Bean
    fun geminiClientWrapper(@Value("\${gemini.api-key}") apiKey: String): GenAiClientWrapper {
        val client = Client.builder().apiKey(apiKey).build()
        return GenAiClientWrapperImpl(client)
    }

    // We can expose the raw client if needed by other components, but the wrapper is preferred
    @Bean
    fun rawGeminiClient(@Value("\${gemini.api-key}") apiKey: String): Client {
        return Client.builder().apiKey(apiKey).build()
    }
}

package cl.clinipets.core.config

import cl.clinipets.core.ia.GenAiClientWrapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnMissingBean(GenAiClientWrapper::class)
class NoopIaConfig {

    @Bean
    fun noopGenAiClientWrapper(): GenAiClientWrapper = object : GenAiClientWrapper {
        override fun generateContent(
            model: String,
            content: com.google.genai.types.Content,
            config: com.google.genai.types.GenerateContentConfig?
        ) =
            throw IllegalStateException("IA deshabilitada (noop)")

        override fun generateContent(
            model: String,
            history: List<com.google.genai.types.Content>,
            config: com.google.genai.types.GenerateContentConfig?
        ) =
            throw IllegalStateException("IA deshabilitada (noop)")
    }
}

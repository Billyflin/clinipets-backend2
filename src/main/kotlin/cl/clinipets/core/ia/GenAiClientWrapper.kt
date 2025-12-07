package cl.clinipets.core.ia

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import org.springframework.stereotype.Component

interface GenAiClientWrapper {
    fun generateContent(model: String, content: Content, config: GenerateContentConfig? = null): GenerateContentResponse
    fun generateContent(model: String, history: List<Content>, config: GenerateContentConfig? = null): GenerateContentResponse
}

class GenAiClientWrapperImpl(private val client: Client) : GenAiClientWrapper {
    override fun generateContent(model: String, content: Content, config: GenerateContentConfig?): GenerateContentResponse {
        return client.models.generateContent(model, content, config)
    }

    override fun generateContent(model: String, history: List<Content>, config: GenerateContentConfig?): GenerateContentResponse {
        return client.models.generateContent(model, history, config)
    }
}

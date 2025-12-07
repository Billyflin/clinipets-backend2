package cl.clinipets.core.integration.meta.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Estructura base para enviar mensajes (Text o Interactive)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppMessageReq(
    @JsonProperty("messaging_product")
    val messagingProduct: String = "whatsapp",
    @JsonProperty("recipient_type")
    val recipientType: String = "individual",
    @JsonProperty("to")
    val to: String,
    @JsonProperty("type")
    val type: String, // "text" | "interactive"
    @JsonProperty("text")
    val text: TextContent? = null,
    @JsonProperty("interactive")
    val interactive: InteractiveContent? = null
)

data class TextContent(
    @JsonProperty("preview_url")
    val previewUrl: Boolean = false,
    val body: String
)

// --- Interactive Message Structures ---

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InteractiveContent(
    val type: String, // "button" | "list"
    val header: InteractiveHeader? = null,
    val body: InteractiveBody,
    val footer: InteractiveFooter? = null,
    val action: InteractiveAction
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InteractiveHeader(
    val type: String, // "text" | "image" | "video" | "document"
    val text: String? = null
    // media objects omitted for brevity, add if needed
)

data class InteractiveBody(
    val text: String
)

data class InteractiveFooter(
    val text: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InteractiveAction(
    val button: String? = null, // Required for List messages (Label for the menu button)
    val buttons: List<InteractiveButton>? = null, // Required for Button messages
    val sections: List<InteractiveSection>? = null // Required for List messages
)

// --- Buttons ---

data class InteractiveButton(
    val type: String = "reply",
    val reply: InteractiveButtonReply
)

data class InteractiveButtonReply(
    val id: String,
    val title: String
)

// --- Lists ---

data class InteractiveSection(
    val title: String? = null,
    val rows: List<InteractiveRow>
)

data class InteractiveRow(
    val id: String,
    val title: String,
    val description: String? = null
)

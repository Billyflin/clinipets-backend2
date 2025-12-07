package cl.clinipets.core.integration.meta.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class WebhookObject(
    val `object`: String = "",
    val entry: List<Entry> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Entry(
    val id: String = "",
    val changes: List<Change> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Change(
    val value: ChangeValue? = null,
    val field: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChangeValue(
    @JsonProperty("messaging_product")
    val messagingProduct: String = "",
    val metadata: Metadata? = null,
    val contacts: List<Contact>? = null,
    val messages: List<Message>? = null,
    val statuses: List<Status>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Metadata(
    @JsonProperty("display_phone_number")
    val displayPhoneNumber: String = "",
    @JsonProperty("phone_number_id")
    val phoneNumberId: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Contact(
    val profile: Profile? = null,
    @JsonProperty("wa_id")
    val waId: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Profile(
    val name: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Message(
    val from: String = "",
    val id: String = "",
    val timestamp: String = "",
    val type: String = "",
    val text: TextMessage? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TextMessage(
    val body: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Status(
    val id: String = "",
    val status: String = "",
    val timestamp: String = "",
    @JsonProperty("recipient_id")
    val recipientId: String = ""
)

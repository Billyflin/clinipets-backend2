package cl.clinipets.core.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import java.time.Instant

@JsonTest
class InstantSerializationTest {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `Instant should serialize to ISO-8601 string`() {
        val now = Instant.now()
        val json = objectMapper.writeValueAsString(now)
        println("Serialized Instant: $json")
        
        // Check if it starts with a quote (string) or is a number
        assertTrue(json.startsWith("\""), "Instant should be serialized as a JSON string, but was: $json")
        // strict ISO check could be added but string check is enough to distinguish from timestamp
    }
}

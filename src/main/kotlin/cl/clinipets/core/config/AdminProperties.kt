package cl.clinipets.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.security")
class AdminProperties {
    var adminEmails: List<String> = emptyList()
}

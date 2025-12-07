package cl.clinipets.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "meta")
class MetaProperties {
    var verifyToken: String = ""
    var accessToken: String = ""
    var phoneNumberId: String = ""
}

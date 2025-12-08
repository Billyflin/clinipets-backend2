package cl.clinipets.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "clinipets.clinic")
data class ClinicProperties(
    var name: String = "Clinipets",
    var rut: String = "",
    var address: String = "",
    var phone: String = "",
    var email: String = "",
    var website: String = "",
    var logoPath: String = ""
)

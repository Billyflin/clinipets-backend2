package cl.clinipets.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    var secret: String = "",
    var refreshSecret: String = "",
    var issuer: String = "clinipets",
    var expirationMinutes: Long = 60,
    var refreshExpirationHours: Long = 24,
    var cookieName: String? = null
)

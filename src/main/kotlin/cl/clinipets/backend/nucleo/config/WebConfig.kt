package cl.clinipets.backend.nucleo.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        // Configuración Base (Segura pero flexible para infraestructura local)
        registry.addMapping("/**")
            .allowedOrigins(
                "https://clinipets.cl",
                "https://www.clinipets.cl",
                "https://admin.clinipets.cl",
                // Permitir localhost explícitamente para integraciones locales en la misma máquina
                "http://localhost",
                "https://localhost",
                "http://localhost:8080",
                "http://localhost:3000",
                "http://localhost:5678" // n8n por ejemplo
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }
}

@Configuration
@Profile("dev")
class DevWebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        // Configuración permisiva SOLO para desarrollo
        registry.addMapping("/**")
            .allowedOriginPatterns("*") // Permite localhost:3000, localhost:8080, etc.
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}

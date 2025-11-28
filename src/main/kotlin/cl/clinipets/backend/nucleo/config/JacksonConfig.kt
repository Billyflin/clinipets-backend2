package cl.clinipets.backend.nucleo.config

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun hibernate6Module(): Module = Hibernate6Module().apply {
        // No forzar carga de LAZY; solo ignora proxies al serializar
        disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING)
        // Evita fallar por proxies no inicializados, serializando solo el ID
        enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS)
    }
}

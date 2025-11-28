package cl.clinipets.backend.nucleo.auditoria

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Instant
import java.util.*

@Configuration
@EnableJpaAuditing(
    auditorAwareRef = "auditorAware",
    dateTimeProviderRef = "utcDateTimeProvider"
)
class AuditoriaConfig {

    @Bean
    fun utcDateTimeProvider(): DateTimeProvider = DateTimeProvider {
        Optional.of(Instant.now()) // siempre UTC
    }
}


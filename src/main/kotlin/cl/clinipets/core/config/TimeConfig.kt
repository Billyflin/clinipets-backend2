package cl.clinipets.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Clock
import java.time.Instant
import java.util.Optional

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class TimeConfig {

    @Bean
    fun clock(): Clock {
        return Clock.systemUTC()
    }

    @Bean
    fun auditingDateTimeProvider(clock: Clock): DateTimeProvider {
        return DateTimeProvider { Optional.of(Instant.now(clock)) }
    }
}

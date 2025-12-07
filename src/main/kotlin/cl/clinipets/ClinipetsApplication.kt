package cl.clinipets

import cl.clinipets.core.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
@EnableScheduling
@EnableAsync
@EnableRetry
class ClinipetsApplication

fun main(args: Array<String>) {
    runApplication<ClinipetsApplication>(*args)
}

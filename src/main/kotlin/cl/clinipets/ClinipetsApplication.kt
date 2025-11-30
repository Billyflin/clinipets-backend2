package cl.clinipets

import cl.clinipets.core.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
class ClinipetsApplication

fun main(args: Array<String>) {
    runApplication<ClinipetsApplication>(*args)
}

package cl.clinipets.core.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val firebaseFilter: FirebaseFilter
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/",
                    "/api/public/**",
                    "/actuator/health",
                    "/index.html",
                    "/google-login.html",
                    "/privacy.html",
                    "/static/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**"
                ).permitAll()
                    // All /api/v1/auth endpoints require authentication (provided by FirebaseFilter)
                    .requestMatchers("/api/v1/reservas").authenticated()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
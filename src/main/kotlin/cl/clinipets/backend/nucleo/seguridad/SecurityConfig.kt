package cl.clinipets.backend.nucleo.seguridad

import cl.clinipets.backend.identidad.dominio.Roles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtFilter: JwtAuthFilter,
    private val env: Environment
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        if (env.activeProfiles.contains("dev")) {
            return http
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
        }

        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {

                // --- Públicos esenciales (App Links + salud + estáticos) ---
                it.requestMatchers(HttpMethod.GET, "/.well-known/assetlinks.json").permitAll()
                it.requestMatchers(HttpMethod.GET, "/.well-known/**")
                    .permitAll() // (por si agregas apple-app-site-association)
                it.requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**", "/static/**").permitAll()
                it.requestMatchers("/actuator/health", "/actuator/health/**", "/api/actuator/health").permitAll()
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // preflight CORS

                // --- Login / público ---
                it.requestMatchers("/google-login.html", "/junta-test.html", "/api/auth/google").permitAll()
                it.requestMatchers("/api/auth/me", "/api/auth/refresh", "/api/auth/logout").permitAll()
                it.requestMatchers("/api/publico/**").permitAll()

                // --- Swagger / OpenAPI ---
                it.requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll()

                // --- Descubir ---
                it.requestMatchers("/api/descubrimiento/buscar").permitAll()


                // --- Agenda ---
                it.requestMatchers(HttpMethod.POST, "/api/agenda/reservar").permitAll()
                it.requestMatchers(HttpMethod.PUT, "/api/agenda/reservar/*/confirmar").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.GET, "/api/agenda/disponibilidad/*/proximos-dias").permitAll()

                // Nuevas rutas unificadas
                it.requestMatchers(HttpMethod.POST, "/api/agenda/reservas").hasAnyRole(Roles.CLIENTE)
                it.requestMatchers(HttpMethod.POST, "/api/agenda/mis-reservas")
                    .hasAnyRole(Roles.CLIENTE, Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.PUT, "/api/agenda/reservas/confirmar").authenticated()
                it.requestMatchers(HttpMethod.DELETE, "/api/agenda/reservas/cancelar").authenticated()
                it.requestMatchers(HttpMethod.POST, "/api/agenda/reservas/info").authenticated()


                // --- Mascotas ---
                it.requestMatchers(HttpMethod.POST, "/api/mascotas/razas").permitAll()
                it.requestMatchers("/api/mascotas/**").hasAnyRole(Roles.CLIENTE, Roles.ADMIN)


                // --- Veterinarios ---
                it.requestMatchers(HttpMethod.POST, "/api/veterinarios").permitAll()
                it.requestMatchers(HttpMethod.PUT, "/api/veterinarios/mi-perfil")
                    .hasAnyRole(Roles.VETERINARIO, Roles.CLIENTE)
                it.requestMatchers(HttpMethod.GET, "/api/veterinarios/mi-perfil")
                    .hasAnyRole(Roles.VETERINARIO, Roles.CLIENTE)
                it.requestMatchers(HttpMethod.GET, "/api/veterinarios/mi-disponibilidad").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.PUT, "/api/veterinarios/mi-disponibilidad").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.GET, "/api/veterinarios/mi-catalogo").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.PUT, "/api/veterinarios/mi-catalogo").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.GET, "/api/veterinarios/procedimientos").permitAll()
                it.requestMatchers(HttpMethod.PUT, "/api/veterinarios/*/verificar").permitAll()
//                it.requestMatchers(HttpMethod.PUT, "/api/veterinarios/*/verificar").hasRole(Roles.ADMIN)

                // --- WebSocket handshake ---
                it.requestMatchers(HttpMethod.GET, "/ws").permitAll()

                // --- Clínica centralizada ---
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/clinica/horarios",
                    "/api/clinica/horarios/disponibilidad",
                    "/api/clinica/horarios/slots"
                ).permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/clinica/horarios", "/api/clinica/horarios/excepciones")
                    .hasRole(Roles.ADMIN)
                it.requestMatchers(HttpMethod.GET, "/api/clinica/horarios/excepciones").hasRole(Roles.ADMIN)
                it.requestMatchers(HttpMethod.DELETE, "/api/clinica/horarios/*").hasRole(Roles.ADMIN)
                it.requestMatchers(HttpMethod.DELETE, "/api/clinica/horarios/excepciones/*").hasRole(Roles.ADMIN)

                // Horarios unificado
                it.requestMatchers(HttpMethod.GET, "/api/horarios/disponibilidad", "/api/horarios/slots").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/horarios").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.GET, "/api/horarios").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.POST, "/api/horarios/excepciones").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.GET, "/api/horarios/excepciones").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.DELETE, "/api/horarios/*").hasRole(Roles.VETERINARIO)
                it.requestMatchers(HttpMethod.DELETE, "/api/horarios/excepciones/*").hasRole(Roles.VETERINARIO)

                it.requestMatchers(HttpMethod.POST, "/api/horarios/clinica", "/api/horarios/clinica/excepciones")
                    .hasRole(Roles.ADMIN)
                it.requestMatchers(HttpMethod.GET, "/api/horarios/clinica").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/horarios/clinica/excepciones").hasRole(Roles.ADMIN)
                it.requestMatchers(HttpMethod.DELETE, "/api/horarios/clinica/*").hasRole(Roles.ADMIN)
                it.requestMatchers(HttpMethod.DELETE, "/api/horarios/clinica/excepciones/*").hasRole(Roles.ADMIN)

                // --- Junta debug ---
                it.requestMatchers("/api/junta/debug/**").permitAll()

                // --- Resto autenticado ---
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}

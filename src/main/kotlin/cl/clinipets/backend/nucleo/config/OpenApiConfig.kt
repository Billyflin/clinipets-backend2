package cl.clinipets.backend.nucleo.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuración de OpenAPI/Swagger para la documentación de la API.
 *
 * La documentación interactiva está disponible en:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(apiInfo())
            .servers(
                listOf(
                    Server().url("http://localhost:8080").description("Servidor de Desarrollo"),
                    Server().url("https://clinipets.cl").description("Servidor de Producción")
                )
            )
            .components(
                Components()
                    .addSecuritySchemes("bearerAuth", securityScheme())
            )
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
    }

    private fun apiInfo(): Info {
        return Info()
            .title("CliniPets API")
            .description(
                """
                ## API REST para la plataforma CliniPets
                
                CliniPets es una plataforma integral de gestión veterinaria que conecta a dueños de mascotas 
                con veterinarios profesionales para servicios a domicilio y en clínica.
                
                ### Características principales:
                
                - **Autenticación**: Login con Google OAuth2 + JWT
                - **Gestión de Mascotas**: Registro y seguimiento de mascotas
                - **Catálogo**: Procedimientos veterinarios y ofertas comerciales
                - **Reservas**: Sistema completo de agendamiento de citas
                - **Disponibilidad**: Gestión de horarios de veterinarios
                - **Juntas en Tiempo Real**: Tracking GPS de citas a domicilio
                - **Descubrimiento**: Búsqueda geolocalizada de servicios
                - **Herramientas Clínicas**: Asistencia con IA para diagnóstico y tratamiento
                - **WebSocket**: Actualizaciones en tiempo real para juntas
                
                ### Autenticación
                
                La mayoría de los endpoints requieren autenticación mediante JWT (JSON Web Token).
                
                **Flujo de autenticación:**
                1. Obtener un Google ID Token desde el cliente
                2. Intercambiarlo por un JWT de CliniPets usando `POST /api/auth/google`
                3. Incluir el JWT en el header `Authorization: Bearer {token}` en cada petición
                
                /google-login.html
                
                ### Roles de usuario
                
                - **CLIENTE**: Dueños de mascotas (pueden gestionar mascotas y crear reservas)
                - **VETERINARIO**: Profesionales veterinarios (gestión de agenda, juntas, herramientas clínicas)
                - **ADMIN**: Administradores del sistema (acceso completo)
                
                ### WebSocket
                
                Además de la API REST, el sistema ofrece comunicación en tiempo real vía WebSocket:
                - **Endpoint**: `ws://localhost:8080/ws`
                - **Protocolo**: STOMP sobre WebSocket
                - **Destinos**: `/topic/juntas/{juntaId}` para seguimiento de citas
                
                ### Modelo de datos
                
                El sistema sigue principios de Domain-Driven Design (DDD) con contextos delimitados:
                - **Identidad**: Usuarios, roles y autenticación
                - **Mascotas**: Gestión de mascotas y sus datos
                - **Catálogo**: Procedimientos y ofertas
                - **Agenda**: Reservas, disponibilidad y juntas
                - **Veterinarios**: Perfil y gestión de veterinarios
                - **Descubrimiento**: Búsqueda y exploración
                - **Clínica**: Herramientas profesionales
                
                ### Versionamiento
                
                La API utiliza versionamiento semántico. La versión actual es **1.0.0**.
                
                ### Códigos de estado HTTP
                
                - **200 OK**: Petición exitosa
                - **201 Created**: Recurso creado exitosamente
                - **204 No Content**: Operación exitosa sin contenido de respuesta
                - **400 Bad Request**: Datos inválidos o petición malformada
                - **401 Unauthorized**: No autenticado o token inválido
                - **403 Forbidden**: Autenticado pero sin permisos suficientes
                - **404 Not Found**: Recurso no encontrado
                - **500 Internal Server Error**: Error del servidor
            """.trimIndent()
            )
            .version("1.0.0")
            .contact(
                Contact()
                    .name("Equipo CliniPets")
                    .email("soporte@clinipets.cl")
                    .url("https://clinipets.cl")
            )
            .license(
                License()
                    .name("Propietario")
                    .url("https://clinipets.cl/licencia")
            )
    }

    private fun securityScheme(): SecurityScheme {
        return SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("JWT obtenido mediante autenticación con Google")
    }
}

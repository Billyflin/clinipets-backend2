package cl.clinipets.identity.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.DeviceToken
import cl.clinipets.identity.domain.DeviceTokenRepository
import cl.clinipets.identity.domain.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class DeviceTokenRequest(
    @field:NotBlank
    val token: String
)

@RestController
@RequestMapping("/api/v1/device-token")
class DeviceTokenController(
    private val userRepository: UserRepository,
    private val deviceTokenRepository: DeviceTokenRepository
) {

    private val logger = LoggerFactory.getLogger(DeviceTokenController::class.java)

    @Operation(summary = "Registrar token de dispositivo para notificaciones push", operationId = "saveDeviceToken")
    @PostMapping
    @Transactional
    fun saveToken(
        @AuthenticationPrincipal principal: JwtPayload,
        @Valid @RequestBody request: DeviceTokenRequest
    ): ResponseEntity<Void> {
        logger.info("[DEVICE_TOKEN] Registrando token para user {}", principal.email)
        val user = userRepository.findById(principal.userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        // Verificar si el token ya existe (idempotencia)
        val existing = deviceTokenRepository.findByToken(request.token)
        if (existing != null) {
            if (existing.user.id == user.id) {
                logger.info("[DEVICE_TOKEN] Token ya existe para este usuario, actualizando fecha")
                existing.lastUpdated = Instant.now()
                deviceTokenRepository.save(existing)
            } else {
                logger.warn("[DEVICE_TOKEN] Token pertenecía a otro usuario, reasignando")
                deviceTokenRepository.delete(existing)
                deviceTokenRepository.save(DeviceToken(user = user, token = request.token))
            }
        } else {
            deviceTokenRepository.save(DeviceToken(user = user, token = request.token))
        }

        return ResponseEntity.ok().build()
    }

    @Operation(summary = "Eliminar token de dispositivo (Logout)", operationId = "deleteDeviceToken")
    @DeleteMapping
    @Transactional
    fun deleteToken(
        @AuthenticationPrincipal principal: JwtPayload,
        @Valid @RequestBody request: DeviceTokenRequest
    ): ResponseEntity<Void> {
        logger.info("[DEVICE_TOKEN] Eliminando token para user {}", principal.email)
        val token = deviceTokenRepository.findByToken(request.token)
        
        if (token != null && token.user.id == principal.userId) {
            deviceTokenRepository.delete(token)
            logger.info("[DEVICE_TOKEN] Token eliminado")
        } else {
            logger.warn("[DEVICE_TOKEN] Token no encontrado o no pertenece al usuario")
        }

        return ResponseEntity.noContent().build()
    }
}
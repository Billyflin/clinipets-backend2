package cl.clinipets.identity.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.UserRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class DeviceTokenRequest(
    @field:NotBlank
    val token: String
)

@RestController
@RequestMapping("/api/v1/device-token")
class DeviceTokenController(
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(DeviceTokenController::class.java)

    @Operation(summary = "Guardar token de dispositivo para notificaciones push", operationId = "saveDeviceToken")
    @PutMapping
    fun saveToken(
        @AuthenticationPrincipal principal: JwtPayload,
        @Valid @RequestBody request: DeviceTokenRequest
    ): ResponseEntity<Void> {
        logger.info("[DEVICE_TOKEN] Guardando token para user {}", principal.email)
        val user = userRepository.findById(principal.userId)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        user.fcmToken = request.token
        userRepository.save(user)

        return ResponseEntity.ok().build()
    }
}

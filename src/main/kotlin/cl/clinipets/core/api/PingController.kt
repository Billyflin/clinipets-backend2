package cl.clinipets.core.api

import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PingResponse(
    val message: String,
    val user: String?
)

@RestController
@RequestMapping("/api")
class PingController() {
    @Operation(summary = "Ping", operationId = "ping")
    @GetMapping("/ping")
    fun ping(@AuthenticationPrincipal principal: JwtPayload?): ResponseEntity<PingResponse> {
        return ResponseEntity.ok(
            PingResponse(
                message = "pong",
                user = principal?.email
            )
        )
    }
}
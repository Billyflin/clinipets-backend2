package cl.clinipets.core.api

import cl.clinipets.core.domain.DateDebug
import cl.clinipets.core.domain.DateDebugRepository
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class PingResponse(
    val message: String,
    val user: String?
)

@RestController
@RequestMapping("/api")
class PingController(
    private val dateDebugRepository: DateDebugRepository
) {
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

    @GetMapping("/public/test-date")
    fun testDate(): ResponseEntity<Map<String, Any>> {
        val now = Instant.now()
        // 1. Save to DB
        val saved = dateDebugRepository.save(DateDebug(id = null, fecha = now))
        // 2. Read back from DB (force fetch)
        val fetched = dateDebugRepository.findById(saved.id!!).get()

        return ResponseEntity.ok(mapOf(
            "original" to now,
            "db_saved" to saved.fecha,
            "db_fetched" to fetched.fecha,
            "iso_check" to "If you see strings like '2023-12-01T...', it works. If numbers, it fails."
        ))
    }
}
package cl.clinipets.backend.agendamiento.api

import cl.clinipets.backend.agendamiento.application.ReservaService
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reservas")
class ReservaController(
    private val reservaService: ReservaService
) {
    @Operation(summary = "Crear reserva", operationId = "crearReserva")
    @PostMapping
    fun crear(
        @Valid @RequestBody request: ReservaCreateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaResponse> {
        val result = reservaService.crearReserva(
            servicioId = request.servicioId,
            mascotaId = request.mascotaId,
            fechaHoraInicio = request.fechaHoraInicio,
            origen = request.origen,
            tutor = principal
        )
        return ResponseEntity.ok(result.cita.toResponse(result.paymentUrl))
    }

    @Operation(summary = "Confirmar reserva", operationId = "confirmarReserva")
    @PatchMapping("/{id}/confirmar")
    fun confirmar(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaResponse> {
        val cita = reservaService.confirmar(id, principal)
        return ResponseEntity.ok(cita.toResponse())
    }

    @Operation(summary = "Listar reservas", operationId = "listarReservas")
    @GetMapping
    fun listar(
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<CitaDetalladaResponse>> {
        return ResponseEntity.ok(reservaService.listar(principal))
    }
}

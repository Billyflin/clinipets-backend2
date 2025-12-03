package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.application.DisponibilidadService
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/disponibilidad")
class DisponibilidadController(
    private val disponibilidadService: DisponibilidadService,
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    @Operation(summary = "Obtener disponibilidad", operationId = "obtenerDisponibilidad")
    @GetMapping
    fun obtener(
        @RequestParam("fecha") fecha: Instant,
        @RequestParam("duracionMinutos") duracionMinutos: Int
    ): ResponseEntity<DisponibilidadResponse> {
        val slots = disponibilidadService.obtenerSlots(fecha, duracionMinutos)
        return ResponseEntity.ok(
            DisponibilidadResponse(
                fecha = fecha,
                servicioId = null, // Deprecated/Optional in response
                slots = slots
            )
        )
    }
}

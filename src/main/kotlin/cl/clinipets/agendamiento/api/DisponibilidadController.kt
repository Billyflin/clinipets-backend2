package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.application.DisponibilidadService
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/disponibilidad")
class DisponibilidadController(
    private val disponibilidadService: DisponibilidadService
) {
    private val logger = LoggerFactory.getLogger(DisponibilidadController::class.java)

    @Operation(summary = "Obtener disponibilidad", operationId = "obtenerDisponibilidad")
    @GetMapping
    fun obtener(
        @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate,
        @RequestParam("duracionMinutos") duracionMinutos: Int
    ): ResponseEntity<DisponibilidadResponse> {
        logger.info("[DISPONIBILIDAD] Request. Fecha: {}, Duracion: {} min", fecha, duracionMinutos)
        val slots = disponibilidadService.obtenerSlots(fecha, duracionMinutos)
        logger.info("[DISPONIBILIDAD] Fin request - Slots encontrados: {}", slots.size)
        return ResponseEntity.ok(
            DisponibilidadResponse(
                fecha = fecha,
                servicioId = null, // Deprecated/Optional in response
                slots = slots
            )
        )
    }
}

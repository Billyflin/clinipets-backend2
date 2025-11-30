package cl.clinipets.backend.agendamiento.api

import cl.clinipets.backend.agendamiento.application.DisponibilidadService
import cl.clinipets.backend.servicios.domain.ServicioMedicoRepository
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/disponibilidad")
class DisponibilidadController(
    private val disponibilidadService: DisponibilidadService,
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    @GetMapping
    fun obtener(
        @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate,
        @RequestParam("servicioId") servicioId: UUID
    ): ResponseEntity<DisponibilidadResponse> {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado") }
        if (!servicio.activo) throw BadRequestException("Servicio inactivo")
        val slots = disponibilidadService.obtenerSlots(fecha, servicio.duracionMinutos)
        return ResponseEntity.ok(
            DisponibilidadResponse(
                fecha = fecha,
                servicioId = servicioId,
                slots = slots
            )
        )
    }
}

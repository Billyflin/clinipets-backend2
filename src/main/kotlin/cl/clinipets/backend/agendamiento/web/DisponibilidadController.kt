package cl.clinipets.backend.agendamiento.web

import cl.clinipets.backend.agendamiento.aplicacion.DisponibilidadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime

@RestController
@RequestMapping("/api/v1/slots")
@Tag(name = "Disponibilidad", description = "Consulta de horarios disponibles")
class DisponibilidadController(
    private val disponibilidadService: DisponibilidadService
) {

    @GetMapping("/disponibles")
    @Operation(
        summary = "Obtener slots disponibles",
        description = "Devuelve lista de horas de inicio disponibles para una fecha dada y un servicio específico."
    )
    fun obtenerDisponibilidad(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate,
        @RequestParam servicioId: Long
    ): List<LocalTime> {
        return disponibilidadService.obtenerSlotsDisponibles(fecha, servicioId)
    }
}

package cl.clinipets.backend.agendamiento.web

import cl.clinipets.backend.agendamiento.aplicacion.AgendamientoService
import cl.clinipets.backend.agendamiento.dominio.Cita
import cl.clinipets.backend.agendamiento.dominio.OrigenCita
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/reservas")
class ReservaController(
    private val agendamientoService: AgendamientoService
) {

    @PostMapping
    fun crearReserva(
        @RequestHeader("X-Platform-Source") source: String,
        @Valid @RequestBody request: ReservaRequest
    ): ResponseEntity<Cita> {
        val origen = try {
            OrigenCita.valueOf(source.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Origen desconocido: $source. Valores permitidos: ${
                    OrigenCita.values().joinToString()
                }"
            )
        }

        val cita = agendamientoService.crearReserva(request, origen)
        return ResponseEntity.status(HttpStatus.CREATED).body(cita)
    }
}

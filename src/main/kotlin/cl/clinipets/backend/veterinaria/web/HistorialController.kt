package cl.clinipets.backend.veterinaria.web

import cl.clinipets.backend.veterinaria.aplicacion.HistorialService
import cl.clinipets.backend.veterinaria.dominio.TipoEvento
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Veterinaria - Historial", description = "Gestión de fichas clínicas y recordatorios")
class HistorialController(
    private val service: HistorialService
) {

    data class CrearEventoRequest(
        val fecha: LocalDate = LocalDate.now(),
        val tipo: TipoEvento,
        val descripcion: String,
        val fechaProximoEvento: LocalDate? = null
    )

    data class EventoResponse(
        val id: Long?,
        val fecha: LocalDate,
        val tipo: TipoEvento,
        val descripcion: String,
        val fechaProximoEvento: LocalDate?,
        val usuarioResponsable: String?,
        val mascotaId: UUID,
        val mascotaNombre: String
    )

    @PostMapping("/mascotas/{id}/historial")
    @Operation(summary = "Registrar evento médico", description = "Agrega una vacuna, consulta o cirugía a la ficha.")
    fun agregarEvento(
        @PathVariable id: UUID,
        @RequestBody req: CrearEventoRequest
    ): ResponseEntity<EventoResponse> {
        // Aquí podrías obtener el usuario del JWT SecurityContext si quisieras auditar quién lo creó
        val usuarioActual = "Sistema/Vet"

        val evento = service.registrarEvento(
            HistorialService.CrearEventoCommand(
                mascotaId = id,
                fecha = req.fecha,
                tipo = req.tipo,
                descripcion = req.descripcion,
                fechaProximoEvento = req.fechaProximoEvento,
                usuarioResponsable = usuarioActual
            )
        )

        return ResponseEntity.ok(
            EventoResponse(
                id = evento.id,
                fecha = evento.fecha,
                tipo = evento.tipo,
                descripcion = evento.descripcion,
                fechaProximoEvento = evento.fechaProximoEvento,
                usuarioResponsable = evento.usuarioResponsable,
                mascotaId = evento.mascota.id!!,
                mascotaNombre = evento.mascota.nombre
            )
        )
    }

    @GetMapping("/mascotas/{id}/historial")
    @Operation(summary = "Ver ficha clínica", description = "Obtiene todo el historial ordenado por fecha descendente.")
    fun verHistorial(@PathVariable id: UUID): ResponseEntity<List<EventoResponse>> {
        val lista = service.obtenerHistorial(id)
        val response = lista.map {
            EventoResponse(
                id = it.id,
                fecha = it.fecha,
                tipo = it.tipo,
                descripcion = it.descripcion,
                fechaProximoEvento = it.fechaProximoEvento,
                usuarioResponsable = it.usuarioResponsable,
                mascotaId = it.mascota.id!!,
                mascotaNombre = it.mascota.nombre
            )
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/veterinaria/recordatorios")
    @Operation(
        summary = "Buscar recordatorios (n8n)",
        description = "Devuelve eventos cuyo 'próximo control' vence entre hoy y la fecha límite dada."
    )
    fun buscarRecordatorios(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) hasta: LocalDate
    ): ResponseEntity<List<EventoResponse>> {
        val lista = service.buscarRecordatorios(hasta)
        val response = lista.map {
            EventoResponse(
                id = it.id,
                fecha = it.fecha,
                tipo = it.tipo,
                descripcion = it.descripcion,
                fechaProximoEvento = it.fechaProximoEvento,
                usuarioResponsable = it.usuarioResponsable,
                mascotaId = it.mascota.id!!,
                mascotaNombre = it.mascota.nombre
            )
        }
        return ResponseEntity.ok(response)
    }
}

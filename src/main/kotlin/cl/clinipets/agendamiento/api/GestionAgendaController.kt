package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.application.GestionAgendaService
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/gestion-agenda")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Gestión de Agenda", description = "Endpoints para gestión avanzada del flujo de atención")
class GestionAgendaController(
    private val gestionService: GestionAgendaService
) {

    @PatchMapping("/{citaId}/iniciar-atencion")
    @Operation(summary = "Inicia la atención del paciente en el box")
    fun iniciarAtencion(
        @PathVariable citaId: UUID,
        @AuthenticationPrincipal staff: JwtPayload
    ): CitaDetalladaResponse {
        val cita = gestionService.iniciarAtencion(citaId, staff)
        return cita.toDetalladaResponse()
    }
}

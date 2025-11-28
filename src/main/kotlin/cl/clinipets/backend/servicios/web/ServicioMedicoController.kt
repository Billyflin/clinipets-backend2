package cl.clinipets.backend.servicios.web

import cl.clinipets.backend.servicios.infraestructura.ServicioMedicoRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/servicios")
@Tag(name = "Servicios Médicos", description = "Gestión de servicios y precios")
class ServicioMedicoController(
    private val servicioRepository: ServicioMedicoRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(
        summary = "Listar servicios",
        description = "Obtiene todos los servicios médicos con sus reglas de precios."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Lista de servicios",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ServicioMedicoDTO::class)
                )]
            )
        ]
    )
    fun listarServicios(): List<ServicioMedicoDTO> {
        return servicioRepository.findAll()
            .map { ServicioMedicoDTO.fromEntity(it) }
    }
}

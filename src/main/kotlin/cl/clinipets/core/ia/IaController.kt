package cl.clinipets.core.ia

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ResumenAtencionRequest(
    @field:NotBlank(message = "La anamnesis es obligatoria")
    val anamnesis: String,
    @field:NotBlank(message = "El diagnóstico es obligatorio")
    val diagnostico: String,
    @field:NotBlank(message = "El tratamiento es obligatorio")
    val tratamiento: String,
    @field:NotBlank(message = "El nombre de la mascota es obligatorio")
    val nombreMascota: String
)

data class ResumenAtencionResponse(
    val mensaje: String
)

@RestController
@RequestMapping("/api/v1/ia")
class IaController(
    private val veterinaryAgentService: VeterinaryAgentService
) {
    private val logger = LoggerFactory.getLogger(IaController::class.java)

    @Operation(
        summary = "Generar resumen de atención para WhatsApp con IA",
        operationId = "generarResumenAtencion"
    )
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping("/generar-resumen")
    fun generarResumen(
        @Valid @RequestBody request: ResumenAtencionRequest
    ): ResponseEntity<ResumenAtencionResponse> {
        logger.info("[IA_RESUMEN] Request recibida para mascota {}", request.nombreMascota)

        val mensaje = veterinaryAgentService.generarResumenWhatsapp(
            anamnesis = request.anamnesis,
            diagnostico = request.diagnostico,
            tratamiento = request.tratamiento,
            nombreMascota = request.nombreMascota
        )

        return ResponseEntity.ok(ResumenAtencionResponse(mensaje))
    }
}

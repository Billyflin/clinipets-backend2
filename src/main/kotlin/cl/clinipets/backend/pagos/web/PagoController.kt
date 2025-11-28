package cl.clinipets.backend.pagos.web

import cl.clinipets.backend.pagos.infraestructura.MercadoPagoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/pagos")
@Tag(name = "Pagos", description = "Gestión de pagos con MercadoPago")
class PagoController(
    private val mercadoPagoService: MercadoPagoService
) {

    data class CrearPagoRequest(
        @field:NotBlank(message = "El título es obligatorio")
        val titulo: String,

        @field:NotNull(message = "El precio es obligatorio")
        @field:DecimalMin(value = "1.0", message = "El precio debe ser mayor a 0")
        val precio: BigDecimal,

        @field:NotNull(message = "El ID de reserva es obligatorio")
        val idReserva: Long
    )

    data class CrearPagoResponse(
        val initPoint: String
    )

    @PostMapping("/crear")
    @Operation(
        summary = "Crear Link de Pago",
        description = "Genera una preferencia de pago en MercadoPago y devuelve el link (initPoint)."
    )
    fun crearPago(@Valid @RequestBody request: CrearPagoRequest): ResponseEntity<CrearPagoResponse> {
        val url = mercadoPagoService.crearPreferencia(request.titulo, request.precio, request.idReserva)
        return ResponseEntity.status(HttpStatus.CREATED).body(CrearPagoResponse(url))
    }
}

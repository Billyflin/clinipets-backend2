package cl.clinipets.agendamiento.api

import cl.clinipets.agendamiento.application.ReservaService
import cl.clinipets.agendamiento.domain.MetodoPago
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

data class FinalizarCitaRequest(
    val metodoPago: MetodoPago? = null
)

@RestController
@RequestMapping("/api/v1/reservas")
class ReservaController(
    private val reservaService: ReservaService
) {
    private val logger = LoggerFactory.getLogger(ReservaController::class.java)

    @Operation(summary = "Obtener resumen financiero diario (Staff/Admin)", operationId = "obtenerResumenDiario")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/admin/resumen")
    fun obtenerResumenDiario(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate
    ): ResponseEntity<ResumenDiarioResponse> {
        logger.info("[RESUMEN_DIARIO] Request. Fecha: {}", fecha)
        val resumen = reservaService.obtenerResumenDiario(fecha)
        return ResponseEntity.ok(resumen)
    }

    @Operation(summary = "Crear reserva (Carrito)", operationId = "crearReserva")
    @PostMapping
    fun crear(
        @Valid @RequestBody request: ReservaCreateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaResponse> {
        logger.info("[CREAR_RESERVA] Request recibida. Tutor: {}", principal.email)
        val result = reservaService.crearReserva(
            detallesRequest = request.detalles,
            fechaHoraInicio = request.fechaHoraInicio,
            origen = request.origen,
            tutor = principal,
            tipoAtencion = request.tipoAtencion,
            direccion = request.direccion,
            pagoTotal = request.pagoTotal
        )
        logger.info("[CREAR_RESERVA] Fin request - Exitoso. ID Cita: {}", result.cita.id)
        return ResponseEntity.ok(result.cita.toResponse(result.paymentUrl))
    }

    @Operation(summary = "Confirmar reserva", operationId = "confirmarReserva")
    @PatchMapping("/{id}/confirmar")
    fun confirmar(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaResponse> {
        logger.info("[CONFIRMAR_RESERVA] Request. ID: {}", id)
        val cita = reservaService.confirmar(id, principal)
        logger.info("[CONFIRMAR_RESERVA] Fin request - Exitoso")
        return ResponseEntity.ok(cita.toResponse(cita.paymentUrl))
    }

    @Operation(summary = "Cancelar reserva", operationId = "cancelarReserva")
    @DeleteMapping("/{id}")
    fun cancelar(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaResponse> {
        logger.info("[CANCELAR_RESERVA] Request. ID: {}", id)
        val cita = reservaService.cancelar(id, principal)
        logger.info("[CANCELAR_RESERVA] Fin request - Exitoso")
        return ResponseEntity.ok(cita.toResponse(cita.paymentUrl))
    }

    @Operation(summary = "Listar reservas", operationId = "listarReservas")
    @GetMapping
    fun listar(
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<CitaDetalladaResponse>> {
        logger.info("[LISTAR_RESERVAS] Request. Tutor: {}", principal.email)
        val response = reservaService.listar(principal)
        logger.info("[LISTAR_RESERVAS] Fin request - Encontradas: {}", response.size)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Obtener reserva por ID", operationId = "obtenerReserva")
    @GetMapping("/{id}")
    fun obtener(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaDetalladaResponse> {
        logger.info("[OBTENER_RESERVA] Request. ID: {}, User: {}", id, principal.email)
        val response = reservaService.obtenerReserva(id, principal)
        logger.info("[OBTENER_RESERVA] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Obtener historial de citas de una mascota", operationId = "historialMascota")
    @GetMapping("/mascota/{mascotaId}")
    fun historialMascota(
        @PathVariable mascotaId: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<CitaDetalladaResponse>> {
        logger.info("[HISTORIAL_MASCOTA] Request. MascotaID: {}, User: {}", mascotaId, principal.email)
        val response = reservaService.obtenerHistorialMascota(mascotaId, principal)
        logger.info("[HISTORIAL_MASCOTA] Fin request - Registros: {}", response.size)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Finalizar cita y registrar pago saldo (Staff/Admin)", operationId = "finalizarCita")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping("/{id}/finalizar")
    fun finalizar(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: FinalizarCitaRequest?,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaResponse> {
        val metodoPago = request?.metodoPago
        logger.info("[FINALIZAR_CITA] Request. ID: {}, User: {}, Metodo: {}", id, principal.email, metodoPago)
        val cita = reservaService.finalizarCita(id, metodoPago, principal)
        logger.info("[FINALIZAR_CITA] Fin request - Exitoso")
        return ResponseEntity.ok(cita.toResponse(cita.paymentUrl))
    }

    @Operation(summary = "Cancelar reserva (Staff/Admin)", operationId = "cancelarReservaPorStaff")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @DeleteMapping("/gestion/{id}")
    fun cancelarPorStaff(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<CitaResponse> {
        logger.info("[CANCELAR_RESERVA_STAFF] Request. ID: {}, User: {}", id, principal.email)
        val cita = reservaService.cancelarPorStaff(id, principal)
        logger.info("[CANCELAR_RESERVA_STAFF] Fin request - Exitoso")
        return ResponseEntity.ok(cita.toResponse(cita.paymentUrl))
    }

    @Operation(summary = "Obtener agenda diaria (Staff/Admin)", operationId = "obtenerAgendaDiaria")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/agenda")
    fun obtenerAgendaDiaria(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate
    ): ResponseEntity<List<CitaDetalladaResponse>> {
        logger.info("[AGENDA_DIARIA] Request. Fecha: {}", fecha)
        val agenda = reservaService.obtenerAgendaDiaria(fecha)
        logger.info("[AGENDA_DIARIA] Fin request - Citas: {}", agenda.size)
        return ResponseEntity.ok(agenda)
    }
}
package cl.clinipets.veterinaria.api

import cl.clinipets.veterinaria.api.MascotaClinicalUpdateRequest
import cl.clinipets.veterinaria.api.MascotaCreateRequest
import cl.clinipets.veterinaria.api.MascotaResponse
import cl.clinipets.veterinaria.api.MascotaUpdateRequest
import cl.clinipets.veterinaria.api.PasaporteSaludResponse
import cl.clinipets.veterinaria.application.MascotaService
import cl.clinipets.core.security.JwtPayload
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.slf4j.LoggerFactory
import java.util.UUID

@RestController
@RequestMapping("/api/mascotas")
class MascotaController(
    private val mascotaService: MascotaService
) {
    private val logger = LoggerFactory.getLogger(MascotaController::class.java)

    @Operation(summary = "Consultar pasaporte de salud", operationId = "pasaporteSalud")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Pasaporte obtenido"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @GetMapping("/{id}/pasaporte-salud")
    fun pasaporteSalud(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<PasaporteSaludResponse> {
        logger.info("[PASAPORTE_SALUD] Consulta para mascota: {}", id)
        val response = mascotaService.consultarPasaporteSalud(id, principal)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Consultar historial de signos vitales", operationId = "historialVitals")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Historial obtenido"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @GetMapping("/{id}/historial-vitals")
    fun historialVitals(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<cl.clinipets.veterinaria.api.SignosVitalesDto>> {
        logger.info("[HISTORIAL_VITALS] Consulta para mascota: {}", id)
        val response = mascotaService.consultarHistorialVitals(id, principal)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Crear mascota", operationId = "crearMascota")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Mascota creada"),
        ApiResponse(responseCode = "400", description = "Datos inválidos")
    )
    @PostMapping
    fun crear(
        @Valid @RequestBody request: MascotaCreateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MascotaResponse> {
        logger.info("[CREAR_MASCOTA] Inicio request. Tutor: {}", principal.email)
        val response = mascotaService.crear(request, principal)
        logger.info("[CREAR_MASCOTA] Fin request - Exitoso. MascotaID: {}", response.id)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Listar mascotas", operationId = "listarMascotas")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Lista de mascotas")
    )
    @GetMapping
    fun listar(
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<List<MascotaResponse>> {
        logger.info("[LISTAR_MASCOTAS] Inicio request. Tutor: {}", principal.email)
        val response = mascotaService.listar(principal)
        logger.info("[LISTAR_MASCOTAS] Fin request - Encontradas: {}", response.size)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Buscar mascotas con filtros avanzados (Staff/Admin)", operationId = "buscarMascotas")
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    fun buscar(
        @RequestParam(required = false) nombre: String?,
        @RequestParam(required = false) especie: cl.clinipets.veterinaria.domain.Especie?,
        @RequestParam(required = false) raza: String?,
        @RequestParam(required = false) esterilizado: Boolean?,
        @RequestParam(required = false) tutorId: UUID?,
        @RequestParam(required = false) chip: String?
    ): ResponseEntity<List<MascotaResponse>> {
        logger.info("[BUSCAR_MASCOTAS] Filtros - Nombre: $nombre, Especie: $especie, Raza: $raza, Esterilizado: $esterilizado")
        val response = mascotaService.buscar(nombre, especie, raza, esterilizado, tutorId, chip)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Obtener mascota", operationId = "obtenerMascota")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Mascota encontrada"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @GetMapping("/{id}")
    fun obtener(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MascotaResponse> {
        logger.info("[OBTENER_MASCOTA] Inicio request. ID: {}, Tutor: {}", id, principal.email)
        val response = mascotaService.obtener(id, principal)
        logger.info("[OBTENER_MASCOTA] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Actualizar mascota", operationId = "actualizarMascota")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Mascota actualizada"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MascotaUpdateRequest,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<MascotaResponse> {
        logger.info("[ACTUALIZAR_MASCOTA] Inicio request. ID: {}, Tutor: {}", id, principal.email)
        val response = mascotaService.actualizar(id, request, principal)
        logger.info("[ACTUALIZAR_MASCOTA] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Actualizar datos clínicos (Staff/Admin)", operationId = "actualizarDatosClinicos")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Datos clínicos actualizados"),
        ApiResponse(responseCode = "403", description = "Sin permisos"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @PatchMapping("/{id}/clinico")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    fun actualizarDatosClinicos(
        @PathVariable id: UUID,
        @RequestBody request: MascotaClinicalUpdateRequest
    ): ResponseEntity<MascotaResponse> {
        logger.info("[ACTUALIZAR_CLINICO] Inicio request. ID: {}", id)
        val response = mascotaService.actualizarDatosClinicos(id, request)
        logger.info("[ACTUALIZAR_CLINICO] Fin request - Exitoso")
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Eliminar mascota", operationId = "eliminarMascota")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Mascota eliminada"),
        ApiResponse(responseCode = "404", description = "Mascota no encontrada")
    )
    @DeleteMapping("/{id}")
    fun eliminar(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: JwtPayload
    ): ResponseEntity<Void> {
        logger.info("[ELIMINAR_MASCOTA] Inicio request. ID: {}, Tutor: {}", id, principal.email)
        mascotaService.eliminar(id, principal)
        logger.info("[ELIMINAR_MASCOTA] Fin request - Exitoso")
        return ResponseEntity.noContent().build()
    }
}
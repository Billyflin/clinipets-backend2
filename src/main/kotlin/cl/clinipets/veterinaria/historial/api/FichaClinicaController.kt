package cl.clinipets.veterinaria.historial.api

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.application.FichaClinicaService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/fichas")
class FichaClinicaController(
    private val fichaService: FichaClinicaService,
    private val mascotaRepository: MascotaRepository // Needed for ownership check
) {

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    fun crearFicha(
        @Valid @RequestBody request: FichaCreateRequest,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<FichaResponse> {
        val ficha = fichaService.crearFicha(request, user.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(ficha)
    }

    @GetMapping("/mascota/{mascotaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CLIENT')")
    fun obtenerHistorial(
        @PathVariable mascotaId: UUID,
        @AuthenticationPrincipal user: JwtPayload
    ): ResponseEntity<List<FichaResponse>> {
        
        // If user is CLIENT, must verify ownership
        if (user.role == UserRole.CLIENT) {
             val mascota = mascotaRepository.findById(mascotaId).orElse(null)
             // If mascota doesn't exist, Service will throw NotFound, but here we check access
             if (mascota != null && mascota.tutor.id != user.userId) {
                 throw UnauthorizedException("No tiene permiso para ver el historial de esta mascota.")
             }
        }

        val historial = fichaService.obtenerHistorial(mascotaId)
        return ResponseEntity.ok(historial)
    }
}

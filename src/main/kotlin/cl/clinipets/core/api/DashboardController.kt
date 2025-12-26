package cl.clinipets.core.api

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class EstadisticasResponse(
    val totalClientes: Long,
    val totalMascotas: Long,
    val citasHoy: Int,
    val citasSemana: Int,
    val citasMes: Int,
    val ingresosMes: BigDecimal,
    val topServicios: List<ServicioPopular>
)

data class ServicioPopular(
    val nombre: String,
    val cantidad: Int
)

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Dashboard", description = "Estadísticas generales del sistema")
class DashboardController(
    private val citaRepository: CitaRepository,
    private val mascotaRepository: MascotaRepository,
    private val userRepository: UserRepository,
    private val clinicZoneId: ZoneId
) {

    @Operation(summary = "Obtener estadísticas generales")
    @GetMapping("/estadisticas")
    fun obtenerEstadisticas(): ResponseEntity<EstadisticasResponse> {
        val ahora = Instant.now()
        val hoy = LocalDate.now(clinicZoneId)

        // Contadores básicos
        val totalClientes = userRepository.findAllByRoleIn(listOf(UserRole.CLIENT)).size.toLong()
        val totalMascotas = mascotaRepository.count()

        // Citas por período
        val inicioHoy = hoy.atStartOfDay(clinicZoneId).toInstant()
        val finHoy = hoy.plusDays(1).atStartOfDay(clinicZoneId).toInstant()

        val citasHoy = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(
            inicioHoy, finHoy
        ).count()

        val hace7Dias = ahora.minus(7, ChronoUnit.DAYS)
        val citasSemana = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(
            hace7Dias, ahora
        ).count()

        val hace30Dias = ahora.minus(30, ChronoUnit.DAYS)
        val citasMes = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(
            hace30Dias, ahora
        )

        val ingresosMes = citasMes
            .filter { it.estado == EstadoCita.FINALIZADA }
            .fold(BigDecimal.ZERO) { acc, cita -> acc.add(cita.precioFinal) }

        // Top servicios del mes
        val topServicios = citasMes
            .flatMap { it.detalles }
            .groupingBy { it.servicio.nombre }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { ServicioPopular(it.first, it.second) }

        return ResponseEntity.ok(
            EstadisticasResponse(
                totalClientes = totalClientes,
                totalMascotas = totalMascotas,
                citasHoy = citasHoy,
                citasSemana = citasSemana,
                citasMes = citasMes.size,
                ingresosMes = ingresosMes,
                topServicios = topServicios
            )
        )
    }
}

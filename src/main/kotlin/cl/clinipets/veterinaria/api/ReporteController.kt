package cl.clinipets.veterinaria.api

import cl.clinipets.veterinaria.domain.PlanPreventivoRepository
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RecordatorioPreventivoDto(
    val mascotaId: java.util.UUID,
    val nombreMascota: String,
    val tutorNombre: String,
    val tutorEmail: String,
    val producto: String,
    val fechaRefuerzo: Instant
)

@RestController
@RequestMapping("/api/v1/reportes")
class ReporteController(
    private val planPreventivoRepository: PlanPreventivoRepository,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(ReporteController::class.java)

    @Operation(
        summary = "Obtener recordatorios de preventivos pendientes (Staff/Admin)",
        operationId = "obtenerRecordatorios"
    )
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/recordatorios")
    fun obtenerRecordatorios(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) inicio: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fin: LocalDate
    ): ResponseEntity<List<RecordatorioPreventivoDto>> {
        logger.info("[REPORTE_RECORDATORIOS] Consultando recordatorios entre {} y {}", inicio, fin)

        val instantInicio = inicio.atStartOfDay(clinicZoneId).toInstant()
        val instantFin = fin.plusDays(1).atStartOfDay(clinicZoneId).toInstant()

        val pendientes = planPreventivoRepository.findRecordatoriosPendientes(instantInicio, instantFin)

        val response = pendientes.map {
            RecordatorioPreventivoDto(
                mascotaId = it.mascota.id!!,
                nombreMascota = it.mascota.nombre,
                tutorNombre = it.mascota.tutor.name,
                tutorEmail = it.mascota.tutor.email,
                producto = it.producto,
                fechaRefuerzo = it.fechaRefuerzo!!
            )
        }

        logger.info("[REPORTE_RECORDATORIOS] Encontrados {} recordatorios pendientes", response.size)
        return ResponseEntity.ok(response)
    }
}

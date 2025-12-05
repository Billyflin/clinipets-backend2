package cl.clinipets.veterinaria.historial.application

import cl.clinipets.core.web.NotFoundException
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.api.FichaCreateRequest
import cl.clinipets.veterinaria.historial.api.FichaResponse
import cl.clinipets.veterinaria.historial.api.toResponse
import cl.clinipets.veterinaria.historial.domain.FichaClinica
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FichaClinicaService(
    private val fichaRepository: FichaClinicaRepository,
    private val mascotaRepository: MascotaRepository
) {
    private val logger = LoggerFactory.getLogger(FichaClinicaService::class.java)

    @Transactional
    fun crearFicha(request: FichaCreateRequest, autorId: UUID): FichaResponse {
        logger.debug("[FICHA_SERVICE] Creando ficha para mascota {}", request.mascotaId)
        val mascota = mascotaRepository.findById(request.mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada con ID: ${request.mascotaId}") }

        // Actualizar peso si viene registrado
        if (request.pesoRegistrado != null) {
            logger.info("[FICHA_SERVICE] Actualizando peso mascota: {} -> {}", mascota.pesoActual, request.pesoRegistrado)
            mascota.pesoActual = java.math.BigDecimal.valueOf(request.pesoRegistrado)
            mascotaRepository.save(mascota)
        }

        val ficha = fichaRepository.save(
            FichaClinica(
                mascota = mascota,
                fechaAtencion = request.fechaAtencion,
                motivoConsulta = request.motivoConsulta,
                anamnesis = request.anamnesis,
                examenFisico = request.examenFisico,
                tratamiento = request.tratamiento,
                pesoRegistrado = request.pesoRegistrado,
                observaciones = request.observaciones,
                diagnostico = request.diagnostico,
                esVacuna = request.esVacuna,
                nombreVacuna = request.nombreVacuna,
                fechaProximaVacuna = request.fechaProximaVacuna,
                fechaProximoControl = request.fechaProximoControl,
                fechaDesparasitacion = request.fechaDesparasitacion,
                autorId = autorId
            )
        )
        logger.info("[FICHA_SERVICE] Ficha guardada con ID: {}", ficha.id)
        return ficha.toResponse()
    }

    @Transactional(readOnly = true)
    fun obtenerHistorial(mascotaId: UUID): List<FichaResponse> {
        // Verificamos que la mascota exista
        if (!mascotaRepository.existsById(mascotaId)) {
            logger.warn("[FICHA_SERVICE] Intento de obtener historial de mascota inexistente: {}", mascotaId)
            throw NotFoundException("Mascota no encontrada con ID: $mascotaId")
        }
        return fichaRepository.findAllByMascotaIdOrderByFechaAtencionDesc(mascotaId)
            .map { it.toResponse() }
    }
}

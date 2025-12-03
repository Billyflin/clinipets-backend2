package cl.clinipets.veterinaria.historial.application

import cl.clinipets.core.web.NotFoundException
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.api.FichaCreateRequest
import cl.clinipets.veterinaria.historial.api.FichaResponse
import cl.clinipets.veterinaria.historial.api.toResponse
import cl.clinipets.veterinaria.historial.domain.FichaClinica
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FichaClinicaService(
    private val fichaRepository: FichaClinicaRepository,
    private val mascotaRepository: MascotaRepository
) {

    @Transactional
    fun crearFicha(request: FichaCreateRequest, autorId: UUID): FichaResponse {
        val mascota = mascotaRepository.findById(request.mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada con ID: ${request.mascotaId}") }

        val ficha = fichaRepository.save(
            FichaClinica(
                mascota = mascota,
                fechaAtencion = request.fechaAtencion,
                motivoConsulta = request.motivoConsulta,
                observaciones = request.observaciones,
                diagnostico = request.diagnostico,
                esVacuna = request.esVacuna,
                nombreVacuna = request.nombreVacuna,
                fechaProximaDosis = request.fechaProximaDosis,
                autorId = autorId
            )
        )
        return ficha.toResponse()
    }

    @Transactional(readOnly = true)
    fun obtenerHistorial(mascotaId: UUID): List<FichaResponse> {
        // Verificamos que la mascota exista
        if (!mascotaRepository.existsById(mascotaId)) {
            throw NotFoundException("Mascota no encontrada con ID: $mascotaId")
        }
        return fichaRepository.findAllByMascotaIdOrderByFechaAtencionDesc(mascotaId)
            .map { it.toResponse() }
    }
}

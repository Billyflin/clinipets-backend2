package cl.clinipets.veterinaria.historial.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.api.*
import cl.clinipets.veterinaria.historial.domain.FichaClinica
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FichaClinicaService(
    private val fichaRepository: FichaClinicaRepository,
    private val mascotaRepository: MascotaRepository,
    private val citaRepository: CitaRepository
) {
    private val logger = LoggerFactory.getLogger(FichaClinicaService::class.java)

    @Transactional
    fun crearFicha(request: FichaCreateRequest, autorId: UUID): FichaResponse {
        logger.debug("[FICHA_SERVICE] Creando ficha estructurada para mascota {}", request.mascotaId)
        val mascota = mascotaRepository.findById(request.mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada con ID: ${request.mascotaId}") }

        // Lógica de Alerta Veterinaria (Ej: Fiebre)
        val tieneAlerta = request.temperatura != null && request.temperatura > 39.5

        if (tieneAlerta) {
            logger.warn("[FICHA_SERVICE] ¡ALERTA! Mascota {} con temperatura elevada: {}", mascota.id, request.temperatura)
        }

        // Actualizar peso de la mascota si viene en la ficha
        if (request.pesoRegistrado != null && request.pesoRegistrado > 0) {
            logger.info("[FICHA_SERVICE] Actualizando peso mascota: {} -> {}", mascota.pesoActual, request.pesoRegistrado)
            mascota.pesoActual = request.pesoRegistrado
            // recalcularPrecioCitaActiva(mascota) // Opcional si se quiere automatizar el cambio de precio
        }

        val ficha = fichaRepository.save(
            FichaClinica(
                mascota = mascota,
                citaId = request.citaId,
                fechaAtencion = request.fechaAtencion,
                motivoConsulta = request.motivoConsulta,
                anamnesis = request.anamnesis,
                hallazgosObjetivos = request.hallazgosObjetivos,
                avaluoClinico = request.avaluoClinico,
                planTratamiento = request.planTratamiento,
                pesoRegistrado = request.pesoRegistrado,
                temperatura = request.temperatura,
                frecuenciaCardiaca = request.frecuenciaCardiaca,
                frecuenciaRespiratoria = request.frecuenciaRespiratoria,
                alertaVeterinaria = tieneAlerta,
                observaciones = request.observaciones,
                esVacuna = request.esVacuna,
                nombreVacuna = request.nombreVacuna,
                fechaProximaVacuna = request.fechaProximaVacuna,
                fechaProximoControl = request.fechaProximoControl,
                fechaDesparasitacion = request.fechaDesparasitacion,
                autorId = autorId
            )
        )

        // Si hay una cita asociada, moverla a EN_ATENCION si aún está CONFIRMADA
        request.citaId?.let { cId ->
            citaRepository.findById(cId).ifPresent { cita ->
                if (cita.estado == EstadoCita.CONFIRMADA) {
                    try {
                        cita.cambiarEstado(EstadoCita.EN_ATENCION, autorId.toString())
                        citaRepository.save(cita)
                    } catch (e: Exception) {
                        logger.warn("No se pudo cambiar estado de cita $cId: ${e.message}")
                    }
                }
            }
        }

        logger.info("[FICHA_SERVICE] Ficha estructurada guardada con ID: {}", ficha.id)
        return ficha.toResponse()
    }

    @Transactional
    fun actualizarFicha(fichaId: UUID, request: FichaUpdateRequest): FichaResponse {
        logger.debug("[FICHA_SERVICE] Actualizando ficha {}", fichaId)
        val ficha = fichaRepository.findById(fichaId)
            .orElseThrow { NotFoundException("Ficha clínica no encontrada") }

        // Mapeo selectivo (solo si vienen datos en el request)
        val updated = ficha.copy(
            anamnesis = request.anamnesis ?: ficha.anamnesis,
            hallazgosObjetivos = request.hallazgosObjetivos ?: ficha.hallazgosObjetivos,
            avaluoClinico = request.avaluoClinico ?: ficha.avaluoClinico,
            planTratamiento = request.planTratamiento ?: ficha.planTratamiento,
            pesoRegistrado = request.pesoRegistrado ?: ficha.pesoRegistrado,
            temperatura = request.temperatura ?: ficha.temperatura,
            frecuenciaCardiaca = request.frecuenciaCardiaca ?: ficha.frecuenciaCardiaca,
            frecuenciaRespiratoria = request.frecuenciaRespiratoria ?: ficha.frecuenciaRespiratoria,
            observaciones = request.observaciones ?: ficha.observaciones,
            fechaProximaVacuna = request.fechaProximaVacuna ?: ficha.fechaProximaVacuna,
            fechaProximoControl = request.fechaProximoControl ?: ficha.fechaProximoControl,
            fechaDesparasitacion = request.fechaDesparasitacion ?: ficha.fechaDesparasitacion,
            // Recalcular alerta si cambió temperatura
            alertaVeterinaria = (request.temperatura ?: ficha.temperatura)?.let { it > 39.5 } ?: ficha.alertaVeterinaria
        )

        // Actualizar peso en mascota si cambió
        if (request.pesoRegistrado != null && request.pesoRegistrado > 0) {
            ficha.mascota.pesoActual = request.pesoRegistrado
        }

        return fichaRepository.save(updated).toResponse()
    }

    @Transactional(readOnly = true)
    fun obtenerHistorial(mascotaId: UUID, pageable: Pageable): Page<FichaResponse> {
        if (!mascotaRepository.existsById(mascotaId)) {
            throw NotFoundException("Mascota no encontrada con ID: $mascotaId")
        }
        return fichaRepository.findAllByMascotaIdOrderByFechaAtencionDesc(mascotaId, pageable)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun obtenerHistorialPeso(mascotaId: UUID): PesoHistoryResponse {
        logger.debug("[FICHA_SERVICE] Obteniendo historial de peso para mascota {}", mascotaId)
        val fichas = fichaRepository.findAllByMascotaIdOrderByFechaAtencionAsc(mascotaId)
        
        val puntos = fichas
            .filter { it.pesoRegistrado != null }
            .map { PesoPunto(it.fechaAtencion, it.pesoRegistrado!!) }

        return PesoHistoryResponse(mascotaId, puntos)
    }

    @Transactional(readOnly = true)
    fun obtenerFichaPorCita(citaId: UUID): FichaResponse {
        val ficha = fichaRepository.findByCitaId(citaId)
            ?: throw NotFoundException("No hay ficha clínica asociada a la cita $citaId")
        return ficha.toResponse()
    }
}
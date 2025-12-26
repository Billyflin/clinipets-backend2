package cl.clinipets.veterinaria.historial.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.api.*
import cl.clinipets.veterinaria.historial.domain.FichaClinica
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import cl.clinipets.veterinaria.historial.domain.PlanSanitario
import cl.clinipets.veterinaria.historial.domain.SignosVitalesData
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
    private val citaRepository: CitaRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(FichaClinicaService::class.java)

    @Transactional
    fun crearFicha(request: FichaCreateRequest, autorId: UUID): FichaResponse {
        logger.debug("[FICHA_SERVICE] Creando ficha estructurada para mascota {}", request.mascotaId)
        val mascota = mascotaRepository.findById(request.mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada con ID: ${request.mascotaId}") }

        val autor = userRepository.findById(autorId)
            .orElseThrow { NotFoundException("Usuario autor no encontrado") }

        // Cargar Cita si existe
        val citaEntity = request.citaId?.let { 
            citaRepository.findById(it).orElse(null) 
        }

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

        val signosVitales = SignosVitalesData(
            pesoRegistrado = request.pesoRegistrado,
            temperatura = request.temperatura,
            frecuenciaCardiaca = request.frecuenciaCardiaca,
            frecuenciaRespiratoria = request.frecuenciaRespiratoria,
            alertaVeterinaria = tieneAlerta
        )

        val planSanitario = PlanSanitario(
            esVacuna = request.esVacuna,
            nombreVacuna = request.nombreVacuna,
            fechaProximaVacuna = request.fechaProximaVacuna,
            fechaProximoControl = request.fechaProximoControl,
            fechaDesparasitacion = request.fechaDesparasitacion
        )

        val ficha = fichaRepository.save(
            FichaClinica(
                mascota = mascota,
                cita = citaEntity,
                fechaAtencion = request.fechaAtencion,
                motivoConsulta = request.motivoConsulta,
                anamnesis = request.anamnesis,
                hallazgosObjetivos = request.hallazgosObjetivos,
                avaluoClinico = request.avaluoClinico,
                planTratamiento = request.planTratamiento,
                signosVitales = signosVitales,
                observaciones = request.observaciones,
                planSanitario = planSanitario,
                autor = autor
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

        // Recalcular alerta si cambió temperatura
        val nuevaTemp = request.temperatura ?: ficha.signosVitales.temperatura
        val nuevaAlerta = nuevaTemp?.let { it > 39.5 } ?: ficha.signosVitales.alertaVeterinaria

        val nuevosSignos = ficha.signosVitales.copy(
            pesoRegistrado = request.pesoRegistrado ?: ficha.signosVitales.pesoRegistrado,
            temperatura = request.temperatura ?: ficha.signosVitales.temperatura,
            frecuenciaCardiaca = request.frecuenciaCardiaca ?: ficha.signosVitales.frecuenciaCardiaca,
            frecuenciaRespiratoria = request.frecuenciaRespiratoria ?: ficha.signosVitales.frecuenciaRespiratoria,
            alertaVeterinaria = nuevaAlerta
        )

        val nuevoPlan = ficha.planSanitario.copy(
            fechaProximaVacuna = request.fechaProximaVacuna ?: ficha.planSanitario.fechaProximaVacuna,
            fechaProximoControl = request.fechaProximoControl ?: ficha.planSanitario.fechaProximoControl,
            fechaDesparasitacion = request.fechaDesparasitacion ?: ficha.planSanitario.fechaDesparasitacion
        )

        val updated = ficha.copy(
            anamnesis = request.anamnesis ?: ficha.anamnesis,
            hallazgosObjetivos = request.hallazgosObjetivos ?: ficha.hallazgosObjetivos,
            avaluoClinico = request.avaluoClinico ?: ficha.avaluoClinico,
            planTratamiento = request.planTratamiento ?: ficha.planTratamiento,
            observaciones = request.observaciones ?: ficha.observaciones,
            signosVitales = nuevosSignos,
            planSanitario = nuevoPlan
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
            .filter { it.signosVitales.pesoRegistrado != null }
            .map { PesoPunto(it.fechaAtencion, it.signosVitales.pesoRegistrado!!) }

        return PesoHistoryResponse(mascotaId, puntos)
    }

    @Transactional(readOnly = true)
    fun obtenerFichaPorCita(citaId: UUID): FichaResponse {
        val ficha = fichaRepository.findByCitaId(citaId)
            ?: throw NotFoundException("No hay ficha clínica asociada a la cita $citaId")
        return ficha.toResponse()
    }
}
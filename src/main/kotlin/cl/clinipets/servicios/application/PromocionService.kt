package cl.clinipets.servicios.application

import cl.clinipets.core.web.NotFoundException
import cl.clinipets.servicios.api.PromocionCreateRequest
import cl.clinipets.servicios.api.PromocionResponse
import cl.clinipets.servicios.api.PromocionUpdateRequest
import cl.clinipets.servicios.api.toResponse
import cl.clinipets.servicios.domain.Promocion
import cl.clinipets.servicios.domain.PromocionBeneficio
import cl.clinipets.servicios.domain.PromocionRepository
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class PromocionService(
    private val promocionRepository: PromocionRepository,
    private val servicioMedicoRepository: ServicioMedicoRepository
) {
    private val logger = LoggerFactory.getLogger(PromocionService::class.java)

    @Transactional(readOnly = true)
    fun listarTodas(): List<PromocionResponse> {
        return promocionRepository.findAll().map { it.toResponse() }
    }

    @Transactional
    fun crear(request: PromocionCreateRequest): PromocionResponse {
        logger.info("[PROMOCION] Creando nueva promoción: {}", request.nombre)

        val promocion = Promocion(
            nombre = request.nombre,
            descripcion = request.descripcion,
            fechaInicio = request.fechaInicio,
            fechaFin = request.fechaFin,
            diasPermitidos = request.diasPermitidos,
            activa = true
        )

        if (request.serviciosTriggerIds.isNotEmpty()) {
            val triggers = servicioMedicoRepository.findAllById(request.serviciosTriggerIds)
            promocion.serviciosTrigger.addAll(triggers)
        }

        request.beneficios.forEach { b ->
            val servicio = servicioMedicoRepository.findById(b.servicioId)
                .orElseThrow { NotFoundException("Servicio para beneficio no encontrado: ${b.servicioId}") }
            promocion.beneficios.add(
                PromocionBeneficio(
                    servicio = servicio,
                    tipo = b.tipo,
                    valor = b.valor
                )
            )
        }

        return promocionRepository.save(promocion).toResponse()
    }

    @Transactional
    fun actualizar(id: UUID, request: PromocionUpdateRequest): PromocionResponse {
        logger.info("[PROMOCION] Actualizando promoción: {}", id)
        val promo = promocionRepository.findById(id)
            .orElseThrow { NotFoundException("Promoción no encontrada: $id") }

        request.nombre?.let { promo.nombre = it }
        request.descripcion?.let { promo.descripcion = it }
        request.fechaInicio?.let { promo.fechaInicio = it }
        request.fechaFin?.let { promo.fechaFin = it }
        request.diasPermitidos?.let { promo.diasPermitidos = it }
        request.activa?.let { promo.activa = it }

        return promocionRepository.save(promo).toResponse()
    }

    @Transactional
    fun eliminar(id: UUID) {
        logger.info("[PROMOCION] Eliminando promoción: {}", id)
        if (!promocionRepository.existsById(id)) {
            throw NotFoundException("Promoción no encontrada: $id")
        }
        promocionRepository.deleteById(id)
    }
}

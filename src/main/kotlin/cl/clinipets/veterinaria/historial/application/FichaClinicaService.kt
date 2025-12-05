package cl.clinipets.veterinaria.historial.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
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
    private val mascotaRepository: MascotaRepository,
    private val citaRepository: CitaRepository
) {
    private val logger = LoggerFactory.getLogger(FichaClinicaService::class.java)

    @Transactional
    fun crearFicha(request: FichaCreateRequest, autorId: UUID): FichaResponse {
        logger.debug("[FICHA_SERVICE] Creando ficha para mascota {}", request.mascotaId)
        val mascota = mascotaRepository.findById(request.mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada con ID: ${request.mascotaId}") }

        // Actualizar peso si viene registrado
        if (request.pesoRegistrado != null) {
            val nuevoPeso = java.math.BigDecimal.valueOf(request.pesoRegistrado)
            logger.info("[FICHA_SERVICE] Actualizando peso mascota: {} -> {}", mascota.pesoActual, nuevoPeso)
            mascota.pesoActual = nuevoPeso
            mascotaRepository.save(mascota)

            // Recálculo de precio en Cita Activa (si existe)
            recalcularPrecioCitaActiva(mascota)
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

    private fun recalcularPrecioCitaActiva(mascota: cl.clinipets.veterinaria.domain.Mascota) {
        val citas = citaRepository.findAllByMascotaId(mascota.id!!)
        
        val citaActiva = citas.firstOrNull { 
            it.estado != EstadoCita.CANCELADA && it.estado != EstadoCita.FINALIZADA
        }

        if (citaActiva != null) {
            logger.info("[FICHA_SERVICE] Recalculando precios para cita activa: {}", citaActiva.id)
            var totalCalculado = 0
            var huboCambios = false
            
            citaActiva.detalles.forEach { detalle ->
                var precio = detalle.precioUnitario
                val servicio = detalle.servicio
                
                if (servicio.requierePeso && detalle.mascota?.id == mascota.id) {
                    val nuevoPrecio = servicio.calcularPrecioPara(mascota)
                    if (nuevoPrecio != precio) {
                        logger.info("   -> Servicio {}: Precio cambia de {} a {}", servicio.nombre, precio, nuevoPrecio)
                        detalle.precioUnitario = nuevoPrecio
                        precio = nuevoPrecio
                        huboCambios = true
                    }
                }
                totalCalculado += precio
            }

            if (huboCambios) {
                logger.info("[FICHA_SERVICE] Nuevo Total Cita: {} (Antes: {})", totalCalculado, citaActiva.precioFinal)
                citaActiva.precioFinal = totalCalculado
                citaRepository.save(citaActiva)
            }
        }
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

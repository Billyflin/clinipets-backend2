package cl.clinipets.veterinaria.historial.application

import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.EstadoDetalleCita
import cl.clinipets.core.web.ConflictException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.historial.api.*
import cl.clinipets.veterinaria.historial.domain.FichaClinica
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import cl.clinipets.veterinaria.historial.domain.PlanSanitario
import cl.clinipets.veterinaria.historial.domain.SignosVitalesData
import cl.clinipets.veterinaria.historial.domain.RecetaMedica
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
    private val userRepository: UserRepository,
    private val hitoMedicoRepository: cl.clinipets.veterinaria.domain.HitoMedicoRepository,
    private val inventarioService: cl.clinipets.servicios.application.InventarioService,
    private val insumoRepository: cl.clinipets.servicios.domain.InsumoRepository,
    private val recetaMedicaRepository: cl.clinipets.veterinaria.historial.domain.RecetaMedicaRepository
) {
    private val logger = LoggerFactory.getLogger(FichaClinicaService::class.java)

    @Transactional
    fun crearFicha(request: FichaCreateRequest, autorId: UUID): FichaResponse {
        logger.debug("[FICHA_SERVICE] Creando ficha estructurada para mascota {}", request.mascotaId)
        val mascota = mascotaRepository.findById(request.mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada con ID: ${request.mascotaId}") }

        val autor = userRepository.findById(autorId)
            .orElseThrow { NotFoundException("Usuario autor no encontrado") }

        // 1. Actualizar Marcadores e Hitos
        request.marcadores?.forEach { (k, v) ->
            val valorAnterior = mascota.marcadores[k]
            if (valorAnterior != v) {
                logger.info("[CLINICAL_RULES] Actualizando marcador: {} -> {} para mascota {}", k, v, mascota.nombre)
                mascota.marcadores[k] = v
                
                hitoMedicoRepository.save(cl.clinipets.veterinaria.domain.HitoMedico(
                    mascota = mascota,
                    marcador = k,
                    valorAnterior = valorAnterior,
                    valorNuevo = v,
                    motivo = "Actualización via Ficha Clínica",
                    citaId = request.citaId
                ))
            }
        }

        // Cargar Cita si existe
        val citaEntity = request.citaId?.let { 
            citaRepository.findById(it).orElse(null) 
        }

        // 2. Procesar Exclusiones Clínicas si hay cita activa
        citaEntity?.let { cita ->
            // Buscar servicios cuya condición de marcador ya no se cumpla
            val detallesParaCancelar = cita.detalles.filter { detalle ->
                val s = detalle.servicio
                if (s.condicionMarcadorClave != null) {
                    val valorActual = mascota.marcadores[s.condicionMarcadorClave]
                    valorActual != s.condicionMarcadorValor && detalle.estado == EstadoDetalleCita.PROGRAMADO
                } else false
            }

            detallesParaCancelar.forEach { detalle ->
                logger.warn("[CLINICAL_RULES] Cancelando servicio '{}' por contraindicación clínica (Marcador {} != {})", 
                    detalle.servicio.nombre, detalle.servicio.condicionMarcadorClave, detalle.servicio.condicionMarcadorValor)
                detalle.estado = EstadoDetalleCita.CANCELADO_CLINICO
                
                // DEVOLUCIÓN DE STOCK (Pilar 2)
                inventarioService.devolverStock(detalle.servicio)
                logger.info("[CLINICAL_RULES] Stock devuelto para servicio cancelado: {}", detalle.servicio.nombre)
            }

            if (detallesParaCancelar.isNotEmpty()) {
                cita.recalcularPrecioFinal()
                logger.info("[CLINICAL_RULES] Precio de cita recalclulado tras exclusiones.")
            }
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
                examenFisico = request.examenFisico?.toEntity() ?: cl.clinipets.veterinaria.historial.domain.ExamenFisico(),
                avaluoClinico = request.avaluoClinico,
                planTratamiento = request.planTratamiento,
                signosVitales = signosVitales,
                observaciones = request.observaciones,
                planSanitario = planSanitario,
                autor = autor
            )
        )

        // Procesar recetas si vienen
        if (request.recetas.isNotEmpty()) {
            procesarRecetas(ficha, request.recetas)
        }

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

    private fun procesarRecetas(ficha: FichaClinica, recetasRequest: List<RecetaRequest>) {
        recetasRequest.forEach { rr ->
            val receta = RecetaMedica(ficha = ficha, observaciones = rr.observaciones)
            rr.items.forEach { ir ->
                val insumo = insumoRepository.findById(ir.insumoId)
                    .orElseThrow { NotFoundException("Insumo no encontrado: ${ir.insumoId}") }

                // VALIDACIÓN CLÍNICA: Contraindicaciones
                insumo.contraindicacionMarcador?.let { marcador ->
                    val valorActual = ficha.mascota.marcadores[marcador]
                    if (valorActual != null && (valorActual.equals("SI", true) || valorActual.equals("POSITIVO", true))) {
                        throw ConflictException("CONTRAINDICACIÓN: No se puede recetar ${insumo.nombre}. La mascota tiene marcador $marcador activo.")
                    }
                }

                val item = cl.clinipets.veterinaria.historial.domain.ItemPrescripcion(
                    receta = receta,
                    insumo = insumo,
                    dosis = ir.dosis,
                    frecuencia = ir.frecuencia,
                    duracion = ir.duracion,
                    cantidadADespachar = ir.cantidadADespachar
                )
                receta.items.add(item)

                // Consumir Stock inmediatamente (porque se asume que se entrega en box o farmacia)
                if (ir.cantidadADespachar > 0) {
                    inventarioService.consumirStockInsumo(
                        insumoId = insumo.id!!,
                        cantidad = ir.cantidadADespachar,
                        referencia = "Receta en Ficha ${ficha.id}"
                    )
                }
            }
            ficha.recetas.add(receta)
            recetaMedicaRepository.save(receta)
        }
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
            examenFisico = request.examenFisico?.toEntity() ?: ficha.examenFisico,
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

        // Actualizar marcadores si vienen
        request.marcadores?.forEach { (k, v) ->
            val valorAnterior = ficha.mascota.marcadores[k]
            if (valorAnterior != v) {
                ficha.mascota.marcadores[k] = v
                hitoMedicoRepository.save(cl.clinipets.veterinaria.domain.HitoMedico(
                    mascota = ficha.mascota,
                    marcador = k,
                    valorAnterior = valorAnterior,
                    valorNuevo = v,
                    motivo = "Actualización via Edición de Ficha"
                ))
            }
        }

        // Procesar recetas nuevas
        request.recetas?.let {
            procesarRecetas(ficha, it)
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
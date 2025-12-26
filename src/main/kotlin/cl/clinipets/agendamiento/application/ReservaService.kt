package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.api.*
import cl.clinipets.agendamiento.domain.*
import cl.clinipets.agendamiento.domain.events.ReservaCanceladaEvent
import cl.clinipets.agendamiento.domain.events.ReservaConfirmadaEvent
import cl.clinipets.agendamiento.domain.events.ReservaCreadaEvent
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.application.InventarioService
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import org.springframework.context.ApplicationEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ReservaResult(val cita: Cita)

@Service
class ReservaService(
    private val citaRepository: CitaRepository,
    private val disponibilidadService: DisponibilidadService,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val mascotaRepository: MascotaRepository,
    private val inventarioService: InventarioService,
    private val pricingCalculator: PricingCalculator,
    private val clinicalValidator: ClinicalValidator,
    private val signosVitalesRepository: cl.clinipets.veterinaria.domain.SignosVitalesRepository,
    private val clinicZoneId: ZoneId,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(ReservaService::class.java)

    private data class TempDetalle(
        val servicio: cl.clinipets.servicios.domain.ServicioMedico,
        val mascota: cl.clinipets.veterinaria.domain.Mascota?,
        val precio: Int
    )

    @Transactional(readOnly = true)
    fun obtenerResumenDiario(fecha: LocalDate): ResumenDiarioResponse {
        logger.info("[RESUMEN_DIARIO] Request. Fecha: $fecha")
        val startOfDay = fecha.atStartOfDay(clinicZoneId).toInstant()
        val endOfDay = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()

        val citas = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(startOfDay, endOfDay)
        
        // Filter cancelled out for calculations to avoid noise
        val citasValidas = citas.filter { it.estado != EstadoCita.CANCELADA }

        val totalCitas = citasValidas.size
        val citasFinalizadas = citasValidas.count { it.estado == EstadoCita.FINALIZADA }

        // 1. Recaudación Realizada (Solo finalizadas)
        val recaudacionTotalRealizada = citasValidas
            .filter { it.estado == EstadoCita.FINALIZADA }
            .sumOf { it.precioFinal }

        // 2. Proyección Pendiente (Confirmadas pero no finalizadas)
        val proyeccionPendiente = citasValidas
            .filter { it.estado == EstadoCita.CONFIRMADA }
            .sumOf { it.precioFinal }

        // 3. Desglose de Métodos de Pago (Solo finalizadas)
        val desgloseMetodosPago = citasValidas
            .filter { it.estado == EstadoCita.FINALIZADA && it.metodoPagoSaldo != null }
            .groupingBy { it.metodoPagoSaldo!! }
            .eachCount()

        val totalGeneral = recaudacionTotalRealizada

        logger.info("[RESUMEN_DIARIO] Realizado: $recaudacionTotalRealizada, Proyectado: $proyeccionPendiente")

        return ResumenDiarioResponse(
            totalCitas = totalCitas,
            citasFinalizadas = citasFinalizadas,
            recaudacionTotalRealizada = recaudacionTotalRealizada,
            proyeccionPendiente = proyeccionPendiente,
            desgloseMetodosPago = desgloseMetodosPago,
            totalGeneral = totalGeneral
        )
    }

    @Transactional
    fun crearReserva(
        detalles: List<ReservaItemRequest>,
        fechaHoraInicio: Instant,
        origen: OrigenCita,
        tutor: JwtPayload,
        tipoAtencion: TipoAtencion = TipoAtencion.CLINICA,
        direccion: String? = null,
        pagoTotal: Boolean = false,
        motivoConsulta: String? = null
    ): ReservaResult {
        logger.info(">>> [CREAR_RESERVA] Inicio. Tutor: ${tutor.email}, Detalles: ${detalles.size}, Fecha: $fechaHoraInicio")

        if (detalles.isEmpty()) throw BadRequestException("Debe incluir al menos un servicio")
        if (tipoAtencion == TipoAtencion.DOMICILIO && direccion.isNullOrBlank()) {
             throw BadRequestException("Debe especificar una dirección para atención a domicilio")
        }

        val serviciosIds = detalles.map { it.servicioId }.toSet()
        val mascotasIds = detalles.map { it.mascotaId }.toSet()

        val serviciosMap = servicioMedicoRepository.findAllById(serviciosIds).associateBy { it.id!! }
        val mascotasMap = mascotaRepository.findAllById(mascotasIds).associateBy { it.id!! }

        if (serviciosMap.size < serviciosIds.size) {
            val missing = serviciosIds - serviciosMap.keys
            throw NotFoundException("Servicios no encontrados: $missing")
        }
        if (mascotasMap.size < mascotasIds.size) {
            val missing = mascotasIds - mascotasMap.keys
            throw NotFoundException("Mascotas no encontradas: $missing")
        }

        // === VALIDAR STOCK ANTES DE PROCEDER ===
        logger.info("[RESERVA] Validando disponibilidad de stock...")
        detalles.forEach { item ->
            val disponible = inventarioService.validarDisponibilidadStock(item.servicioId, item.cantidad)
            if (!disponible) {
                val servicio = serviciosMap[item.servicioId]!!
                throw BadRequestException("Stock insuficiente para: ${servicio.nombre}")
            }
        }
        logger.info("[RESERVA] Stock validado correctamente")

        val fechaCita = fechaHoraInicio.atZone(clinicZoneId).toLocalDate()
        var totalPrecio = 0
        var duracionTotalMinutos = 0L
        val tempList = mutableListOf<TempDetalle>()

        for (item in detalles) {
            val mascota = mascotasMap[item.mascotaId]!!
            if (mascota.tutor.id != tutor.userId) throw UnauthorizedException("La mascota ${mascota.nombre} no te pertenece")

            val servicio = serviciosMap[item.servicioId]!!
            if (!servicio.activo) throw BadRequestException("Servicio inactivo: ${servicio.nombre}")

            clinicalValidator.validarRequisitosClinicos(servicio, mascota, serviciosIds)

            val precioCalculado = pricingCalculator.calcularPrecioFinal(servicio, mascota, fechaCita)

            val precioItem = precioCalculado.precioFinal * item.cantidad
            totalPrecio += precioItem
            duracionTotalMinutos += (servicio.duracionMinutos * item.cantidad)

            repeat(item.cantidad) {
                tempList.add(TempDetalle(servicio, mascota, precioCalculado.precioFinal))
            }
        }

        // Validación de Disponibilidad - Validar que TODOS los slots necesarios estén disponibles
        val fechaNormalizada = fechaHoraInicio.truncatedTo(ChronoUnit.MINUTES)
        if (duracionTotalMinutos > 0) {
            val slotsDisponibles = disponibilidadService.obtenerSlots(fechaCita, duracionTotalMinutos.toInt())

            // Calcular todos los slots de 15 minutos que necesita la cita
            val finRequerido = fechaNormalizada.plus(duracionTotalMinutos, ChronoUnit.MINUTES)
            val slotsNecesarios = mutableListOf<Instant>()
            var cursor = fechaNormalizada

            while (cursor < finRequerido) {
                slotsNecesarios.add(cursor)
                cursor = cursor.plus(15, ChronoUnit.MINUTES) // Intervalos de 15 min
            }

            // Verificar que TODOS los slots necesarios estén disponibles
            val todosDisponibles = slotsNecesarios.all { slot ->
                slotsDisponibles.any { it.compareTo(slot) == 0 }
            }

            if (!todosDisponibles) {
                throw BadRequestException(
                    "No hay disponibilidad continua para la duración total ($duracionTotalMinutos min). " +
                    "Algunos slots están ocupados."
                )
            }

            logger.info("[RESERVA] Validados ${slotsNecesarios.size} slots continuos desde $fechaNormalizada")
        }

        val fechaHoraFin = fechaNormalizada.plus(duracionTotalMinutos, ChronoUnit.MINUTES)
        val cita = Cita(
            fechaHoraInicio = fechaNormalizada,
            fechaHoraFin = fechaHoraFin,
            estado = EstadoCita.CONFIRMADA,
            precioFinal = totalPrecio,
            tutorId = tutor.userId,
            origen = origen,
            tipoAtencion = tipoAtencion,
            motivoConsulta = motivoConsulta,
            direccion = direccion
        )

        tempList.forEach { temp ->
            cita.detalles.add(
                DetalleCita(
                    cita = cita,
                    servicio = temp.servicio,
                    mascota = temp.mascota,
                    precioUnitario = temp.precio
                )
            )
        }

        val guardada = citaRepository.save(cita)

        eventPublisher.publishEvent(ReservaCreadaEvent(guardada.id!!))
        return ReservaResult(guardada)
    }

    @Transactional
    fun confirmar(id: UUID, tutor: JwtPayload): Cita {
        logger.info("[CONFIRMAR_RESERVA] Request para CitaID: {}", id)
        val cita = citaRepository.findById(id).orElseThrow { NotFoundException("Cita no encontrada") }
        if (cita.tutorId != tutor.userId) {
            logger.warn("[CONFIRMAR_RESERVA] Usuario {} intentó confirmar cita ajena {}", tutor.email, id)
            throw UnauthorizedException("No puedes confirmar esta cita")
        }
        cita.cambiarEstado(EstadoCita.CONFIRMADA, tutor.email)
        val saved = citaRepository.save(cita)

        logger.info("AUDIT: Cita $id movida a CONFIRMADA por ${tutor.email}")
        eventPublisher.publishEvent(ReservaConfirmadaEvent(saved.id!!))
        return saved
    }

    @Transactional
    fun cancelar(id: UUID, tutor: JwtPayload): Cita {
        logger.info("[CANCELAR_RESERVA] Request para CitaID: {}", id)
        val cita = citaRepository.findById(id).orElseThrow { NotFoundException("Cita no encontrada") }
        if (cita.tutorId != tutor.userId) {
            logger.warn("[CANCELAR_RESERVA] Usuario {} intentó cancelar cita ajena {}", tutor.email, id)
            throw UnauthorizedException("No puedes cancelar esta cita")
        }

        // Solo permitimos cancelar citas que no estén ya canceladas o completadas
        if (cita.estado == EstadoCita.CANCELADA) {
            logger.warn("[CANCELAR_RESERVA] Cita {} ya estaba cancelada", id)
            throw BadRequestException("La cita ya está cancelada")
        }

        // Reponer stock si corresponde
        reponerStock(cita)

        cita.cambiarEstado(EstadoCita.CANCELADA, tutor.email)
        val saved = citaRepository.save(cita)

        logger.info("AUDIT: Cita $id movida a CANCELADA por ${tutor.email}")
        eventPublisher.publishEvent(ReservaCanceladaEvent(saved.id!!, "El tutor canceló la reserva"))
        return saved
    }

    private fun reponerStock(cita: Cita) {
        cita.detalles.forEach { detalle ->
            logger.info("[STOCK] Reponiendo stock/insumos para: {}", detalle.servicio.nombre)
            inventarioService.devolverStock(detalle.servicio)
        }
    }

    @Transactional
    fun listar(tutor: JwtPayload): List<CitaDetalladaResponse> {
        val citas = citaRepository.findAllByTutorIdOrderByFechaHoraInicioDesc(tutor.userId)
        return citas.map { it.toDetalladaResponse() }
    }

    @Transactional
    fun obtenerReserva(id: UUID, solicitante: JwtPayload): CitaDetalladaResponse {
        logger.info("[OBTENER_RESERVA] Request. ID: {}, Solicitante: {}", id, solicitante.email)
        val cita = citaRepository.findById(id).orElseThrow { NotFoundException("Cita no encontrada") }

        // Security check: Allow if user is Staff/Admin OR if user is the tutor (owner)
        if (solicitante.role != UserRole.STAFF && solicitante.role != UserRole.ADMIN) {
            if (cita.tutorId != solicitante.userId) {
                logger.warn("[OBTENER_RESERVA] Acceso denegado. User: {} intentó acceder a Cita: {}", solicitante.email, id)
                throw UnauthorizedException("No tienes permiso para ver esta reserva")
            }
        }
        logger.info("[OBTENER_RESERVA] Acceso permitido. Retornando detalle.")
        return cita.toDetalladaResponse()
    }

    @Transactional
    fun obtenerHistorialMascota(mascotaId: UUID, solicitante: JwtPayload): List<CitaDetalladaResponse> {
        logger.info("[HISTORIAL_MASCOTA] Request. MascotaID: {}, Solicitante: {}", mascotaId, solicitante.email)

        // Security Check
        if (solicitante.role != UserRole.STAFF && solicitante.role != UserRole.ADMIN) {
            val mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow { NotFoundException("Mascota no encontrada") }
            
            if (mascota.tutor.id != solicitante.userId) {
                logger.warn("[HISTORIAL_MASCOTA] Acceso denegado. User {} no es dueño de Mascota {}", solicitante.email, mascotaId)
                throw UnauthorizedException("No tienes permiso para ver el historial de esta mascota")
            }
        }

        val citas = citaRepository.findAllByMascotaId(mascotaId)
        logger.info("[HISTORIAL_MASCOTA] Encontradas {} citas para mascota {}", citas.size, mascotaId)
        return citas.map { it.toDetalladaResponse() }
    }

    // NOT @Transactional at method level to allow manual handling of rollback scenario
    fun finalizarCita(id: UUID, request: FinalizarCitaRequest?, staff: JwtPayload): Cita {
        val metodoPago = request?.metodoPago
        logger.info("[FINALIZAR_CITA] Request. CitaID: {}, Staff: {}, Metodo: {}", id, staff.email, metodoPago)
        
        // Validate permissions
        if (staff.role != UserRole.STAFF && staff.role != UserRole.ADMIN) {
            logger.warn("[FINALIZAR_CITA] Acceso denegado. User {} no es Staff/Admin", staff.email)
            throw UnauthorizedException("No tienes permisos para finalizar citas")
        }

        val cita = citaRepository.findById(id).orElseThrow { NotFoundException("Cita no encontrada") }

        if (cita.estado == EstadoCita.FINALIZADA || cita.estado == EstadoCita.CANCELADA) {
            logger.warn("[FINALIZAR_CITA] Intento de finalizar cita en estado inválido: {}", cita.estado)
            throw BadRequestException("La cita no se puede finalizar porque está en estado ${cita.estado}")
        }

        return try {
            val saved = transactionTemplate.execute {
                // 1. Consumir stock
                cita.detalles.forEach { detalle ->
                    logger.info("[FINALIZAR] Consumiendo stock para ${detalle.servicio.nombre}")
                    inventarioService.consumirStock(detalle.servicio.id!!, 1, "Cita $id")

                    // Registrar Signos Vitales si vienen en el request
                    val mascotaVal = detalle.mascota
                    if (mascotaVal != null) {
                        request?.signosVitales?.get(mascotaVal.id)?.let { svReq ->
                            val sv = cl.clinipets.veterinaria.domain.SignosVitales(
                                mascota = mascotaVal,
                                peso = svReq.peso,
                                temperatura = svReq.temperatura,
                                frecuenciaCardiaca = svReq.frecuenciaCardiaca
                            )
                            signosVitalesRepository.save(sv)
                            logger.info("[CLINICO] Signos vitales registrados para mascota {}", mascotaVal.id)
                        }
                    }
                }

                // 2. Solo si TODO el stock se consumió exitosamente → Finalizar
                cita.metodoPagoSaldo = metodoPago
                cita.staffFinalizadorId = staff.userId
                cita.cambiarEstado(EstadoCita.FINALIZADA, staff.email)
                
                citaRepository.save(cita)
            }
            logger.info("AUDIT: Cita $id finalizada y stock descontado por staff ${staff.email}")
            saved!!
        } catch (ex: cl.clinipets.core.web.ConflictException) {
            logger.error("[FINALIZAR] Error consumiendo stock: ${ex.message}")

            // Rollback happened automatically for the transactionTemplate block.
            // Stock is restored.
            // We now mark as CANCELADA in a fresh transaction.

            cita.cambiarEstado(EstadoCita.CANCELADA, staff.email)
            citaRepository.save(cita)

            logger.warn("AUDIT: Cita $id marcada como CANCELADA por falta de stock al finalizar. Staff: ${staff.email}")

            throw BadRequestException(
                "No se pudo finalizar la cita: ${ex.message}. " +
                "El stock fue insuficiente al momento de la atención. La cita ha sido cancelada."
            )
        }
    }

    @Transactional
    fun cancelarPorStaff(citaId: UUID, staff: JwtPayload): Cita {
        logger.info("[CANCELAR_STAFF] Iniciando cancelación administrativa. CitaID: {}, Staff: {}", citaId, staff.email)
        val cita = citaRepository.findById(citaId).orElseThrow { NotFoundException("Cita no encontrada") }

        // 1. Validar rol
        if (staff.role != UserRole.STAFF && staff.role != UserRole.ADMIN) {
            logger.warn("[CANCELAR_STAFF] Usuario {} sin permisos de Staff", staff.email)
            throw UnauthorizedException("No tienes permisos para realizar esta acción")
        }

        // 2. Devolver stock
        reponerStock(cita)

        // 3. Cambiar estado
        cita.cambiarEstado(EstadoCita.CANCELADA, staff.email)

        // 4. Guardar y retornar
        val saved = citaRepository.save(cita)
        saved.detalles.size // Forzar carga de detalles

        logger.info("AUDIT: Cita $citaId movida a CANCELADA (Admin) por staff ${staff.email}")
        eventPublisher.publishEvent(ReservaCanceladaEvent(saved.id!!, "Cancelación realizada por el staff"))
        return saved
    }

    @Transactional(readOnly = true)
    fun obtenerAgendaDiaria(fecha: LocalDate): List<CitaDetalladaResponse> {
        val startOfDay = fecha.atStartOfDay(clinicZoneId).toInstant()
        val endOfDay = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()

        val citas = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(startOfDay, endOfDay)

        return citas.map { it.toDetalladaResponse() }
    }

    @Transactional(readOnly = true)
    fun buscarCitas(
        estado: EstadoCita?,
        fechaDesde: LocalDate?,
        fechaHasta: LocalDate?
    ): List<CitaDetalladaResponse> {
        val inicio = fechaDesde?.atStartOfDay(clinicZoneId)?.toInstant() ?: Instant.EPOCH
        val fin = fechaHasta?.plusDays(1)?.atStartOfDay(clinicZoneId)?.toInstant() ?: Instant.now().plus(365, ChronoUnit.DAYS)

        val citas = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(inicio, fin)

        val filtradas = if (estado != null) {
            citas.filter { it.estado == estado }
        } else {
            citas
        }

        return filtradas.map { it.toDetalladaResponse() }
    }
}
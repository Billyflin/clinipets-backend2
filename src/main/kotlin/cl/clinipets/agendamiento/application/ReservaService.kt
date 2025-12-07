package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.api.CitaDetalladaResponse
import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.agendamiento.api.ResumenDiarioResponse
import cl.clinipets.agendamiento.api.toDetalladaResponse
import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.DetalleCita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.agendamiento.domain.TipoAtencion
import cl.clinipets.agendamiento.domain.MetodoPago
import cl.clinipets.core.integration.NotificationService
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.pagos.application.EstadoPagoMP
import cl.clinipets.pagos.application.PagoService
import cl.clinipets.servicios.application.InventarioService
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ReservaResult(val cita: Cita, val paymentUrl: String?)

@Service
class ReservaService(
    private val citaRepository: CitaRepository,
    private val disponibilidadService: DisponibilidadService,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val mascotaRepository: MascotaRepository,
    private val pagoService: PagoService,
    private val notificationService: NotificationService,
    private val inventarioService: InventarioService,
    private val userRepository: UserRepository,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(ReservaService::class.java)

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
        
        // Recaudación Online: Abonos de todas las citas válidas (pendientes, confirmadas, finalizadas, etc)
        // Asumimos que el abono se pagó al reservar.
        val recaudacionOnline = citasValidas.sumOf { it.montoAbono }
        
        // Recaudación Mostrador: Saldo restante (Total - Abono) SOLO de las finalizadas (que son las que pagaron el resto)
        val recaudacionMostrador = citasValidas
            .filter { it.estado == EstadoCita.FINALIZADA }
            .sumOf { it.precioFinal - it.montoAbono }
            
        val totalGeneral = recaudacionOnline + recaudacionMostrador

        logger.info("[RESUMEN_DIARIO] Total: $totalGeneral (Online: $recaudacionOnline, Mostrador: $recaudacionMostrador)")

        return ResumenDiarioResponse(
            totalCitas = totalCitas,
            citasFinalizadas = citasFinalizadas,
            recaudacionOnline = recaudacionOnline,
            recaudacionMostrador = recaudacionMostrador,
            totalGeneral = totalGeneral
        )
    }

    @Transactional
    fun crearReserva(
        detallesRequest: List<DetalleReservaRequest>,
        fechaHoraInicio: Instant,
        origen: OrigenCita,
        tutor: JwtPayload,
        tipoAtencion: TipoAtencion = TipoAtencion.CLINICA,
        direccion: String? = null,
        pagoTotal: Boolean = false
    ): ReservaResult {
        logger.info(">>> [CREAR_RESERVA] Inicio. Tutor: ${tutor.email}, Items: ${detallesRequest.size}, Fecha: $fechaHoraInicio, PagoTotal: $pagoTotal")

        if (detallesRequest.isEmpty()) throw BadRequestException("Debe incluir al menos un servicio o producto")
        if (tipoAtencion == TipoAtencion.DOMICILIO && direccion.isNullOrBlank()) {
             throw BadRequestException("Debe especificar una dirección para atención a domicilio")
        }

        var totalPrecio = 0
        val abonos = mutableListOf<Int>()
        var duracionTotalMinutos = 0L

        // We need a temporary object to hold the data before creating DetalleCita with the Cita reference
        data class TempDetalle(
            val servicio: cl.clinipets.servicios.domain.ServicioMedico,
            val mascota: cl.clinipets.veterinaria.domain.Mascota?,
            val precio: Int
        )
        val tempList = mutableListOf<TempDetalle>()

        logger.debug(">>> [CREAR_RESERVA] Procesando items del carrito...")

        for (req in detallesRequest) {
            val servicio = servicioMedicoRepository.findById(req.servicioId)
                .orElseThrow { NotFoundException("Servicio no encontrado: ${req.servicioId}") }

            if (!servicio.activo) throw BadRequestException("Servicio inactivo: ${servicio.nombre}")

            // Delegate inventory management
            inventarioService.consumirStock(servicio.id!!)

            var mascota: cl.clinipets.veterinaria.domain.Mascota? = null
            var precioItem = servicio.precioBase

            if (servicio.categoria == CategoriaServicio.PRODUCTO) {
                // Products don't necessarily need a pet, but if provided, check owner
                if (req.mascotaId != null) {
                    mascota = mascotaRepository.findById(req.mascotaId)
                        .orElseThrow { NotFoundException("Mascota no encontrada: ${req.mascotaId}") }
                    if (mascota.tutor.id != tutor.userId) throw UnauthorizedException("La mascota ${mascota.nombre} no te pertenece")
                }
                duracionTotalMinutos += servicio.duracionMinutos
            } else {
                // Services usually require a pet
                if (req.mascotaId == null) throw BadRequestException("El servicio ${servicio.nombre} requiere especificar una mascota")

                mascota = mascotaRepository.findById(req.mascotaId)
                    .orElseThrow { NotFoundException("Mascota no encontrada: ${req.mascotaId}") }

                if (mascota.tutor.id != tutor.userId) throw UnauthorizedException("La mascota ${mascota.nombre} no te pertenece")

                // Delegate price calculation to Domain Entity
                precioItem = servicio.calcularPrecioPara(mascota)
                duracionTotalMinutos += servicio.duracionMinutos
            }

            // Abono logic: Collect deposits. If null (e.g. products), use 0.
            val abonoItem = servicio.precioAbono ?: 0
            abonos.add(abonoItem)
            
            logger.debug("   -> Item: ${servicio.nombre} | Duración: ${servicio.duracionMinutos} min | Precio: $precioItem | Abono: $abonoItem")

            totalPrecio += precioItem
            tempList.add(TempDetalle(servicio, mascota, precioItem))
        }

        // Calculate final deposit
        val abonoMinimo = abonos.maxOrNull() ?: 0
        val montoAbonoFinal = if (pagoTotal) totalPrecio else abonoMinimo

        logger.info(">>> [CREAR_RESERVA] Carrito procesado. Total: $totalPrecio. AbonoMin: $abonoMinimo. A Cobrar: $montoAbonoFinal")

        // Validate Availability for the total duration
        if (duracionTotalMinutos > 0) {
            val fechaNormalizada = fechaHoraInicio.truncatedTo(ChronoUnit.MINUTES)

            // Log crucial para debugging de zona horaria
            val fechaLocal = fechaNormalizada.atZone(clinicZoneId).toLocalDate()
            logger.info(">>> [VALIDACION_HORARIO] FechaNormalizada(Instant): $fechaNormalizada -> Convertida a Zona Clínica ($clinicZoneId): $fechaLocal")

            val slotsDisponibles = disponibilidadService.obtenerSlots(fechaLocal, duracionTotalMinutos.toInt())

            val slotValido = slotsDisponibles.any { it.compareTo(fechaNormalizada) == 0 }

            if (!slotValido) {
                logger.warn(">>> [ERROR_DISPONIBILIDAD] El slot solicitado $fechaNormalizada NO se encontró en los slots disponibles")
                throw BadRequestException("El horario seleccionado no está disponible para la duración total ($duracionTotalMinutos min)")
            }

            logger.info(">>> [VALIDACION_HORARIO] Horario disponible confirmado.")

            // Use normalized time for the appointment start/end
            val fechaHoraFin = fechaNormalizada.plus(duracionTotalMinutos, ChronoUnit.MINUTES)

            val cita = Cita(
                fechaHoraInicio = fechaNormalizada,
                fechaHoraFin = fechaHoraFin,
                estado = EstadoCita.PENDIENTE_PAGO,
                precioFinal = totalPrecio,
                montoAbono = montoAbonoFinal,
                tutorId = tutor.userId,
                origen = origen,
                tipoAtencion = tipoAtencion,
                direccion = direccion
            )

            // Now create DetalleCita objects linked to the Cita
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
            logger.info(">>> [CREAR_RESERVA] Cita guardada en BD con ID: ${guardada.id}")

            val tipoCobro = if (pagoTotal) "Pago Total" else "Reserva"
            val paymentUrl = pagoService.crearPreferencia(
                titulo = "Clinipets ($tipoCobro) - ${tempList.size} items",
                precio = guardada.montoAbono,
                externalReference = guardada.id!!.toString()
            )
            guardada.paymentUrl = paymentUrl
            notificarNuevaReserva(guardada, tutor)
            return ReservaResult(guardada, paymentUrl)
        } else {
            // Caso de productos sin duración (Venta directa o retiro)
            logger.info(">>> [CREAR_RESERVA] Duración 0 detectada (Solo productos). Saltando validación de agenda estricta.")

            val cita = Cita(
                fechaHoraInicio = fechaHoraInicio,
                fechaHoraFin = fechaHoraInicio,
                estado = EstadoCita.PENDIENTE_PAGO,
                precioFinal = totalPrecio,
                montoAbono = montoAbonoFinal,
                tutorId = tutor.userId,
                origen = origen,
                tipoAtencion = tipoAtencion,
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
            
            val tipoCobro = if (pagoTotal) "Pago Total" else "Reserva"
            val paymentUrl = pagoService.crearPreferencia(
                titulo = "Clinipets ($tipoCobro) - ${tempList.size} items",
                precio = guardada.montoAbono,
                externalReference = guardada.id!!.toString()
            )
            guardada.paymentUrl = paymentUrl
            notificarNuevaReserva(guardada, tutor)
            return ReservaResult(guardada, paymentUrl)
        }
    }

    @Transactional
    fun confirmar(id: UUID, tutor: JwtPayload): Cita {
        logger.info("[CONFIRMAR_RESERVA] Request para CitaID: {}", id)
        val cita = citaRepository.findById(id).orElseThrow { NotFoundException("Cita no encontrada") }
        if (cita.tutorId != tutor.userId) {
            logger.warn("[CONFIRMAR_RESERVA] Usuario {} intentó confirmar cita ajena {}", tutor.email, id)
            throw UnauthorizedException("No puedes confirmar esta cita")
        }
        cita.estado = EstadoCita.CONFIRMADA
        val saved = citaRepository.save(cita)
        logger.info("[CONFIRMAR_RESERVA] Cita confirmada exitosamente")
        notificarConfirmacion(saved)
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

        cita.estado = EstadoCita.CANCELADA
        val saved = citaRepository.save(cita)
        logger.info("[CANCELAR_RESERVA] Cita cancelada exitosamente")
        notificarStaffCita(saved, "Reserva cancelada", "El tutor canceló la reserva")
        return saved
    }

    private fun reponerStock(cita: Cita) {
        cita.detalles.forEach { detalle ->
            val servicio = detalle.servicio
            if (servicio.categoria == CategoriaServicio.PRODUCTO) {
                logger.info("[STOCK] Reponiendo stock para producto: {}", servicio.nombre)
                inventarioService.devolverStock(servicio)
            }
        }
    }

    @Transactional
    fun listar(tutor: JwtPayload): List<CitaDetalladaResponse> {
        val citas = citaRepository.findAllByTutorIdOrderByFechaHoraInicioDesc(tutor.userId)

        val pendientesRecientes = citas
            .filter { it.estado == EstadoCita.PENDIENTE_PAGO }
            .sortedByDescending { it.createdAt }
            .take(3)

        pendientesRecientes.forEach { cita ->
            val estadoPago = pagoService.consultarEstadoPago(cita.id!!.toString())
            if (estadoPago.estado == EstadoPagoMP.APROBADO) {
                cita.estado = EstadoCita.CONFIRMADA
                cita.mpPaymentId = estadoPago.paymentId
                val saved = citaRepository.save(cita)
                logger.info("Auto-healing listado: Cita {} confirmada", cita.id)
                notificarConfirmacion(saved)
            }
        }

        return citas.map { it.toDetalladaResponse(it.paymentUrl) }
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
        val citaVerificada = autoHealPago(cita)
        logger.info("[OBTENER_RESERVA] Acceso permitido. Retornando detalle.")
        return citaVerificada.toDetalladaResponse(citaVerificada.paymentUrl)
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
        val citasVerificadas = citas.map { autoHealPago(it) }
        return citasVerificadas.map { it.toDetalladaResponse(it.paymentUrl) }
    }

    @Transactional
    fun finalizarCita(id: UUID, metodoPago: MetodoPago?, staff: JwtPayload): Cita {
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

        val saldoPendiente = cita.precioFinal - cita.montoAbono

        if (saldoPendiente > 0 && metodoPago == MetodoPago.MERCADO_PAGO_LINK) {
            logger.info("[FINALIZAR_CITA] Generando link de pago para saldo pendiente: {}", saldoPendiente)
            val paymentUrl = pagoService.crearPreferencia(
                titulo = "Clinipets (Saldo Pendiente) - Cita ${cita.id}",
                precio = saldoPendiente,
                externalReference = "SALDO-${cita.id}"
            )
            cita.paymentUrl = paymentUrl
            cita.estado = EstadoCita.POR_PAGAR
            
            citaRepository.save(cita)
            notificarStaffCita(cita, "Saldo pendiente", "Se generó link de pago por $saldoPendiente para el saldo pendiente")
            return cita
        }

        // For other methods or if balance is 0, finalize immediately
        cita.metodoPagoSaldo = metodoPago
        cita.staffFinalizadorId = staff.userId
        cita.estado = EstadoCita.FINALIZADA
        
        val saved = citaRepository.save(cita)
        logger.info("[FINALIZAR_CITA] Cita finalizada correctamente. Saldo pendiente virtualmente en 0.")
        notificarStaffCita(saved, "Cita finalizada", "Estado actualizado a ${saved.estado} por ${metodoPago ?: "N/A"}")
        return saved
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

        // 2. Reembolso si aplica
        if (cita.mpPaymentId != null) {
            try {
                logger.info("[CANCELAR_STAFF] Procesando reembolso para PaymentID: {}", cita.mpPaymentId)
                val reembolsoExitoso = pagoService.reembolsar(cita.mpPaymentId!!)
                if (reembolsoExitoso) {
                    logger.info("[CANCELAR_STAFF] Reembolso para cita {} procesado exitosamente.", cita.id)
                    // 3. Generar cupón de compensación
                    cita.tokenCompensacion = "DISCULPA-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                    logger.info("[CANCELAR_STAFF] Cupón generado: {}", cita.tokenCompensacion)
                } else {
                    logger.error("[CANCELAR_STAFF] El servicio de pago devolvió 'false' para el reembolso de la cita {}. Requiere revisión manual.", citaId)
                }
            } catch (e: Exception) {
                logger.error("[CANCELAR_STAFF] Fallo reembolso automático para cita ID {}, requiere revisión manual.", citaId, e)
            }
        }

        // 4. Devolver stock
        reponerStock(cita)

        // 5. Cambiar estado
        cita.estado = EstadoCita.CANCELADA

        // 6. Guardar y retornar
        val saved = citaRepository.save(cita)
        saved.detalles.size // Forzar carga de detalles
        logger.info("[CANCELAR_STAFF] Cita cancelada correctamente")
        notificarStaffCita(saved, "Reserva cancelada", "Cancelación realizada por el staff")
        return saved
    }

    @Transactional(readOnly = true)
    fun obtenerAgendaDiaria(fecha: LocalDate): List<CitaDetalladaResponse> {
        val startOfDay = fecha.atStartOfDay(clinicZoneId).toInstant()
        val endOfDay = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()

        val citas = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(startOfDay, endOfDay)

        return citas.map { it.toDetalladaResponse(it.paymentUrl) }
    }

    @Transactional
    fun cancelarReservasExpiradas() {
        val expiracion = Instant.now().minus(30, ChronoUnit.MINUTES)
        logger.info("[CLEANUP] Buscando reservas pendientes creadas antes de {}", expiracion)

        val expiradas = citaRepository.findByEstadoAndCreatedAtBefore(EstadoCita.PENDIENTE_PAGO, expiracion)
        
        if (expiradas.isEmpty()) {
            logger.info("[CLEANUP] No se encontraron reservas expiradas.")
            return
        }

        logger.info("[CLEANUP] Encontradas {} reservas expiradas. Procediendo a cancelar...", expiradas.size)

        for (cita in expiradas) {
            try {
                logger.info("[CLEANUP] Cancelando cita ID: {} (Creada: {})", cita.id, cita.createdAt)
                
                // Replicar lógica de cancelación: Reponer Stock y cambiar estado
                reponerStock(cita)
                cita.estado = EstadoCita.CANCELADA
                citaRepository.save(cita)
                
            } catch (e: Exception) {
                logger.error("[CLEANUP] Error al cancelar cita expirada {}", cita.id, e)
            }
        }
        logger.info("[CLEANUP] Proceso finalizado.")
    }

    private fun autoHealPago(cita: Cita): Cita {
        if (cita.estado != EstadoCita.PENDIENTE_PAGO) return cita
        val referencia = cita.id?.toString() ?: return cita
        val estadoPago = pagoService.consultarEstadoPago(referencia)

        if (estadoPago.estado != EstadoPagoMP.APROBADO) return cita

        cita.estado = EstadoCita.CONFIRMADA
        cita.mpPaymentId = estadoPago.paymentId
        val saved = citaRepository.save(cita)
        logger.info("Auto-healing: Cita {} confirmada al consultar estado", cita.id)
        notificarConfirmacion(saved)
        return saved
    }

    private fun notificarConfirmacion(cita: Cita) {
        val servicioNombre = cita.detalles.firstOrNull()?.servicio?.nombre ?: "tu reserva"
        notificationService.enviarNotificacion(
            cita.tutorId,
            "¡Reserva Confirmada!",
            "Tu cita para $servicioNombre está lista",
            data = mapOf("type" to "CLIENT_RESERVATIONS")
        )
        notificarStaffCita(cita, "Pago confirmado", "La reserva quedó en estado ${cita.estado}")
    }

    private fun notificarNuevaReserva(cita: Cita, tutor: JwtPayload) {
        val citaId = cita.id
        if (citaId == null) {
            logger.warn("[NOTIFS] Intento de notificar nueva reserva sin ID asignado.")
            return
        }
        val tutorNombre = userRepository.findById(tutor.userId)
            .map { it.name }
            .orElse(null)
            ?.takeIf { it.isNotBlank() }
            ?: tutor.email.substringBefore("@")
        val hora = cita.fechaHoraInicio.atZone(clinicZoneId)
            .toLocalTime()
            .truncatedTo(ChronoUnit.MINUTES)
        notificationService.enviarNotificacionAStaff(
            "Nueva Reserva",
            "Cliente $tutorNombre agendó para $hora",
            mapOf(
                "type" to "STAFF_CITA_DETAIL",
                "citaId" to citaId.toString()
            )
        )
    }

    private fun notificarStaffCita(cita: Cita, titulo: String, detalle: String) {
        val resumen = resumenCita(cita)
        val citaId = cita.id ?: "sin-id"
        notificationService.enviarNotificacionAStaff(
            titulo,
            "Cita $citaId: $detalle ($resumen)."
        )
    }

    private fun resumenCita(cita: Cita): String {
        val fechaLocal = cita.fechaHoraInicio.atZone(clinicZoneId)
        val hora = fechaLocal.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        val servicioNombre = cita.detalles.firstOrNull()?.servicio?.nombre ?: "cita"
        return "$servicioNombre el ${fechaLocal.toLocalDate()} a las $hora"
    }
}

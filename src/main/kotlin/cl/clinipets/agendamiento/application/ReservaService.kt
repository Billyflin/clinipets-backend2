package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.api.CitaDetalladaResponse
import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.agendamiento.api.toDetalladaResponse
import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.DetalleCita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
import cl.clinipets.agendamiento.domain.TipoAtencion
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
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
    private val inventarioService: InventarioService,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(ReservaService::class.java)

    @Transactional
    fun crearReserva(
        detallesRequest: List<DetalleReservaRequest>,
        fechaHoraInicio: Instant,
        origen: OrigenCita,
        tutor: JwtPayload,
        tipoAtencion: TipoAtencion = TipoAtencion.CLINICA,
        direccion: String? = null
    ): ReservaResult {
        logger.info(">>> [CREAR_RESERVA] Inicio. Tutor: ${tutor.email}, Items: ${detallesRequest.size}, FechaSolicitada(UTC/Raw): $fechaHoraInicio")

        if (detallesRequest.isEmpty()) throw BadRequestException("Debe incluir al menos un servicio o producto")
        if (tipoAtencion == TipoAtencion.DOMICILIO && direccion.isNullOrBlank()) {
             throw BadRequestException("Debe especificar una dirección para atención a domicilio")
        }

        var totalPrecio = 0
        var totalAbono = 0
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
            inventarioService.consumirStock(servicio)

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

            // Abono logic: use specific deposit price if set, otherwise full price
            val abonoItem = servicio.precioAbono ?: precioItem
            
            logger.debug("   -> Item: ${servicio.nombre} | Duración: ${servicio.duracionMinutos} min | Precio: $precioItem | Abono: $abonoItem")

            totalPrecio += precioItem
            totalAbono += abonoItem
            tempList.add(TempDetalle(servicio, mascota, precioItem))
        }

        logger.info(">>> [CREAR_RESERVA] Carrito procesado. Duración Total: $duracionTotalMinutos min. Precio Total: $totalPrecio. Abono Total: $totalAbono")

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
                montoAbono = totalAbono,
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

            val paymentUrl = pagoService.crearPreferencia(
                titulo = "Reserva Clínica (Abono) - ${tempList.size} items",
                precio = guardada.montoAbono, // Charge ONLY the deposit amount
                externalReference = guardada.id!!.toString()
            )
            guardada.paymentUrl = paymentUrl
            return ReservaResult(guardada, paymentUrl)
        } else {
            // Caso de productos sin duración (Venta directa o retiro)
            logger.info(">>> [CREAR_RESERVA] Duración 0 detectada (Solo productos). Saltando validación de agenda estricta.")

            val cita = Cita(
                fechaHoraInicio = fechaHoraInicio,
                fechaHoraFin = fechaHoraInicio,
                estado = EstadoCita.PENDIENTE_PAGO,
                precioFinal = totalPrecio,
                montoAbono = totalAbono,
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
            val paymentUrl = pagoService.crearPreferencia(
                titulo = "Reserva Clínica (Abono) - ${tempList.size} items",
                precio = guardada.montoAbono,
                externalReference = guardada.id!!.toString()
            )
            guardada.paymentUrl = paymentUrl
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

        // Auto-healing: sincronizar estados de pago pendientes con Mercado Pago
        citas
            .filter { it.estado == EstadoCita.PENDIENTE_PAGO && it.id != null }
            .forEach { cita ->
                try {
                    val resultadoPago = pagoService.consultarEstadoPago(cita.id!!.toString())
                    if (resultadoPago.estado == EstadoPagoMP.APROBADO) {
                        logger.info("[Auto-Healing] Cita ${cita.id} encontrada como PENDIENTE, pero APROBADA en MP. Actualizando a CONFIRMADA.")
                        cita.estado = EstadoCita.CONFIRMADA
                        cita.mpPaymentId = resultadoPago.paymentId
                        citaRepository.save(cita)
                    }
                } catch (ex: Exception) {
                    // No rompemos listar por problemas con MP; solo logueamos
                    logger.error("[AUTO_HEALING] Error consultando estado de pago para cita {}", cita.id, ex)
                }
            }

        return citas.map { it.toDetalladaResponse(it.paymentUrl) }
    }

    @Transactional(readOnly = true)
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
        return cita.toDetalladaResponse(cita.paymentUrl)
    }

    @Transactional(readOnly = true)
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
        return citas.map { it.toDetalladaResponse(it.paymentUrl) }
    }

    @Transactional
    fun finalizarCita(id: UUID, staff: JwtPayload): Cita {
        logger.info("[FINALIZAR_CITA] Request. CitaID: {}, Staff: {}", id, staff.email)
        
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

        // Mark as FINALIZED. Logic assumes full payment at counter.
        cita.estado = EstadoCita.FINALIZADA
        
        val saved = citaRepository.save(cita)
        logger.info("[FINALIZAR_CITA] Cita finalizada correctamente. Saldo pendiente virtualmente en 0.")
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
            logger.info("[CANCELAR_STAFF] Procesando reembolso para PaymentID: {}", cita.mpPaymentId)
            val reembolsoExitoso = pagoService.reembolsar(cita.mpPaymentId!!)
            if (!reembolsoExitoso) {
                logger.error("[CANCELAR_STAFF] Error en reembolso MP para cita {}", citaId)
                throw Exception("Error al procesar el reembolso en Mercado Pago para la cita ${cita.id}. Por favor, revisa manualmente.")
            }
            // 3. Generar cupón de compensación
            cita.tokenCompensacion = "DISCULPA-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
            logger.info("[CANCELAR_STAFF] Cupón generado: {}", cita.tokenCompensacion)
        }

        // 4. Devolver stock
        reponerStock(cita)

        // 5. Cambiar estado
        cita.estado = EstadoCita.CANCELADA

        // 6. Guardar y retornar
        val saved = citaRepository.save(cita)
        logger.info("[CANCELAR_STAFF] Cita cancelada correctamente")
        return saved
    }

    @Transactional(readOnly = true)
    fun obtenerAgendaDiaria(fecha: LocalDate): List<CitaDetalladaResponse> {
        val startOfDay = fecha.atStartOfDay(clinicZoneId).toInstant()
        val endOfDay = fecha.plusDays(1).atStartOfDay(clinicZoneId).toInstant()

        val citas = citaRepository.findAllByFechaHoraInicioBetweenOrderByFechaHoraInicioAsc(startOfDay, endOfDay)

        return citas.map { it.toDetalladaResponse(it.paymentUrl) }
    }
}

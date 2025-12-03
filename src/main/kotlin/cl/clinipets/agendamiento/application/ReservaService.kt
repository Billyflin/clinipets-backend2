package cl.clinipets.agendamiento.application

import cl.clinipets.agendamiento.api.CitaDetalladaResponse
import cl.clinipets.agendamiento.api.DetalleReservaRequest
import cl.clinipets.agendamiento.api.toDetalladaResponse
import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.DetalleCita
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.agendamiento.domain.OrigenCita
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
        tutor: JwtPayload
    ): ReservaResult {
        logger.info(">>> [CREAR_RESERVA] Inicio. Tutor: ${tutor.email}, Items: ${detallesRequest.size}, FechaSolicitada(UTC/Raw): $fechaHoraInicio")

        if (detallesRequest.isEmpty()) throw BadRequestException("Debe incluir al menos un servicio o producto")

        var totalPrecio = 0
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

            logger.debug("   -> Item Agregado: ${servicio.nombre} | Duración: ${servicio.duracionMinutos} min | Precio: $precioItem")

            totalPrecio += precioItem
            tempList.add(TempDetalle(servicio, mascota, precioItem))
        }

        logger.info(">>> [CREAR_RESERVA] Carrito procesado. Duración Total Acumulada: $duracionTotalMinutos min. Precio Total: $totalPrecio")

        // Validate Availability for the total duration
        if (duracionTotalMinutos > 0) {
            val fechaNormalizada = fechaHoraInicio.truncatedTo(ChronoUnit.MINUTES)

            // Log crucial para debugging de zona horaria
            val fechaLocal = fechaNormalizada.atZone(clinicZoneId).toLocalDate()
            logger.info(">>> [VALIDACION_HORARIO] FechaNormalizada(Instant): $fechaNormalizada -> Convertida a Zona Clínica ($clinicZoneId): $fechaLocal")

            val slotsDisponibles = disponibilidadService.obtenerSlots(fechaLocal, duracionTotalMinutos.toInt())

            val slotValido = slotsDisponibles.any { it.compareTo(fechaNormalizada) == 0 }

            if (!slotValido) {
                logger.warn(">>> [ERROR_DISPONIBILIDAD] El slot solicitado $fechaNormalizada NO se encontró en los slots disponibles generados: $slotsDisponibles")
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
                tutorId = tutor.userId,
                origen = origen
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
                titulo = "Reserva Clínica - ${tempList.size} items",
                precio = guardada.precioFinal,
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
                tutorId = tutor.userId,
                origen = origen
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
                titulo = "Reserva Clínica - ${tempList.size} items",
                precio = guardada.precioFinal,
                externalReference = guardada.id!!.toString()
            )
            guardada.paymentUrl = paymentUrl
            return ReservaResult(guardada, paymentUrl)
        }
    }

    @Transactional
    fun confirmar(id: UUID, tutor: JwtPayload): Cita {
        val cita = citaRepository.findById(id).orElseThrow { NotFoundException("Cita no encontrada") }
        if (cita.tutorId != tutor.userId) throw UnauthorizedException("No puedes confirmar esta cita")
        cita.estado = EstadoCita.CONFIRMADA
        return citaRepository.save(cita)
    }

    @Transactional
    fun cancelar(id: UUID, tutor: JwtPayload): Cita {
        val cita = citaRepository.findById(id).orElseThrow { NotFoundException("Cita no encontrada") }
        if (cita.tutorId != tutor.userId) throw UnauthorizedException("No puedes cancelar esta cita")

        // Solo permitimos cancelar citas que no estén ya canceladas o completadas
        if (cita.estado == EstadoCita.CANCELADA) {
            throw BadRequestException("La cita ya está cancelada")
        }

        // Reponer stock si corresponde
        reponerStock(cita)

        cita.estado = EstadoCita.CANCELADA
        return citaRepository.save(cita)
    }

    private fun reponerStock(cita: Cita) {
        cita.detalles.forEach { detalle ->
            val servicio = detalle.servicio
            if (servicio.categoria == CategoriaServicio.PRODUCTO) {
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

    @Transactional
    fun cancelarPorStaff(citaId: UUID, staff: JwtPayload): Cita {
        val cita = citaRepository.findById(citaId).orElseThrow { NotFoundException("Cita no encontrada") }

        // 1. Validar rol
        if (staff.role != UserRole.STAFF && staff.role != UserRole.ADMIN) {
            throw UnauthorizedException("No tienes permisos para realizar esta acción")
        }

        // 2. Reembolso si aplica
        if (cita.mpPaymentId != null) {
            val reembolsoExitoso = pagoService.reembolsar(cita.mpPaymentId!!)
            if (!reembolsoExitoso) {
                throw Exception("Error al procesar el reembolso en Mercado Pago para la cita ${cita.id}. Por favor, revisa manualmente.")
            }
            // 3. Generar cupón de compensación
            cita.tokenCompensacion = "DISCULPA-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
        }

        // 4. Devolver stock
        reponerStock(cita)

        // 5. Cambiar estado
        cita.estado = EstadoCita.CANCELADA

        // 6. Guardar y retornar
        return citaRepository.save(cita)
    }
}
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

    @Transactional(readOnly = true)
    fun listar(tutor: JwtPayload): List<CitaDetalladaResponse> {
        val citas = citaRepository.findAllByTutorIdOrderByFechaHoraInicioDesc(tutor.userId)
        return citas.map { it.toDetalladaResponse() }
    }
}
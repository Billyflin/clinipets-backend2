package cl.clinipets.backend.agendamiento.application

import cl.clinipets.backend.agendamiento.api.CitaDetalladaResponse
import cl.clinipets.backend.agendamiento.api.CitaResponse
import cl.clinipets.backend.agendamiento.api.toDetalladaResponse
import cl.clinipets.backend.agendamiento.api.toResponse
import cl.clinipets.backend.agendamiento.domain.Cita
import cl.clinipets.backend.agendamiento.domain.CitaRepository
import cl.clinipets.backend.agendamiento.domain.EstadoCita
import cl.clinipets.backend.agendamiento.domain.OrigenCita
import cl.clinipets.backend.pagos.application.PagoService
import cl.clinipets.backend.servicios.domain.ReglaPrecio
import cl.clinipets.backend.servicios.domain.ServicioMedicoRepository
import cl.clinipets.backend.veterinaria.domain.MascotaRepository
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ReservaResult(val cita: Cita, val paymentUrl: String?)

@Service
class ReservaService(
    private val citaRepository: CitaRepository,
    private val disponibilidadService: DisponibilidadService,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val mascotaRepository: MascotaRepository,
    private val pagoService: PagoService
) {

    @Transactional
    fun crearReserva(
        servicioId: UUID,
        mascotaId: UUID,
        fechaHoraInicio: Instant,
        origen: OrigenCita,
        tutor: JwtPayload
    ): ReservaResult {
        val servicio = servicioMedicoRepository.findById(servicioId)
            .orElseThrow { NotFoundException("Servicio no encontrado") }
        if (!servicio.activo) throw BadRequestException("Servicio inactivo")

        val mascota = mascotaRepository.findById(mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada") }
        if (mascota.tutor.id != tutor.userId) {
            throw UnauthorizedException("No puedes reservar con esta mascota")
        }

        // Validar disponibilidad. Pasamos la fecha de inicio para que el servicio calcule los slots de ese día.
        val slotValido = disponibilidadService.obtenerSlots(fechaHoraInicio, servicio.duracionMinutos)
            .any { it == fechaHoraInicio }
        if (!slotValido) throw BadRequestException("El horario seleccionado no está disponible")

        val precioFinal = calcularPrecio(servicio.requierePeso, servicio.precioBase, mascota.pesoActual, servicio.reglas.map { it })
        val cita = Cita(
            fechaHoraInicio = fechaHoraInicio,
            fechaHoraFin = fechaHoraInicio.plus(servicio.duracionMinutos.toLong(), ChronoUnit.MINUTES),
            estado = EstadoCita.PENDIENTE_PAGO,
            precioFinal = precioFinal,
            servicioId = servicio.id!!,
            mascotaId = mascota.id!!,
            tutorId = tutor.userId,
            origen = origen
        )
        val guardada = citaRepository.save(cita)
        val paymentUrl = pagoService.crearPreferencia(
            titulo = "Servicio ${servicio.nombre} - ${mascota.nombre}",
            precio = guardada.precioFinal,
            externalReference = guardada.id!!.toString()
        )
        return ReservaResult(guardada, paymentUrl)
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
        if (citas.isEmpty()) return emptyList()

        val serviciosIds = citas.map { it.servicioId }.distinct()
        val mascotasIds = citas.map { it.mascotaId }.distinct()

        val servicios = servicioMedicoRepository.findAllById(serviciosIds).associateBy { it.id }
        val mascotas = mascotaRepository.findAllById(mascotasIds).associateBy { it.id }

        return citas.map { cita ->
            val nombreServicio = servicios[cita.servicioId]?.nombre ?: "Desconocido"
            val nombreMascota = mascotas[cita.mascotaId]?.nombre ?: "Desconocida"
            cita.toDetalladaResponse(nombreServicio, nombreMascota)
        }
    }

    @Transactional(readOnly = true)
    fun listarPorMascota(mascotaId: UUID, tutor: JwtPayload): List<CitaDetalladaResponse> {
        val mascota = mascotaRepository.findById(mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada") }
        if (mascota.tutor.id != tutor.userId) {
            throw UnauthorizedException("No puedes acceder a esta mascota")
        }

        val citas = citaRepository.findAllByMascotaIdOrderByFechaHoraInicioDesc(mascotaId)
        if (citas.isEmpty()) return emptyList()

        val serviciosIds = citas.map { it.servicioId }.distinct()
        val servicios = servicioMedicoRepository.findAllById(serviciosIds).associateBy { it.id }

        return citas.map { cita ->
            val nombreServicio = servicios[cita.servicioId]?.nombre ?: "Desconocido"
            cita.toDetalladaResponse(nombreServicio, mascota.nombre)
        }
    }

    private fun calcularPrecio(
        requierePeso: Boolean,
        precioBase: Int,
        pesoMascota: BigDecimal,
        reglas: List<ReglaPrecio>
    ): Int {
        if (!requierePeso) return precioBase
        val regla = reglas.firstOrNull {
            (pesoMascota >= it.pesoMin) && (pesoMascota <= it.pesoMax)
        }
        return regla?.precio ?: precioBase
    }
}

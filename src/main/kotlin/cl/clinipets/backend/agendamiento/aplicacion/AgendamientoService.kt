package cl.clinipets.backend.agendamiento.aplicacion

import cl.clinipets.backend.agendamiento.dominio.Cita
import cl.clinipets.backend.agendamiento.dominio.EstadoCita
import cl.clinipets.backend.agendamiento.dominio.HorarioClinica
import cl.clinipets.backend.agendamiento.dominio.OrigenCita
import cl.clinipets.backend.agendamiento.dominio.eventos.ReservaCreadaEvent
import cl.clinipets.backend.agendamiento.dominio.excepciones.HorarioNoDisponibleException
import cl.clinipets.backend.agendamiento.infraestructura.CitaRepository
import cl.clinipets.backend.agendamiento.web.ReservaRequest
import cl.clinipets.backend.identidad.dominio.Usuario
import cl.clinipets.backend.identidad.infraestructura.UsuarioRepository
import cl.clinipets.backend.mascotas.dominio.Especie
import cl.clinipets.backend.mascotas.dominio.Mascota
import cl.clinipets.backend.mascotas.infraestructura.MascotaRepository
import cl.clinipets.backend.servicios.dominio.CalculadoraPrecioService
import cl.clinipets.backend.servicios.infraestructura.ServicioMedicoRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class AgendamientoService(
    private val citaRepository: CitaRepository,
    private val mascotaRepository: MascotaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val calculadoraPrecioService: CalculadoraPrecioService,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun crearReserva(request: ReservaRequest, origen: OrigenCita): Cita {
        // 1. Validar Horario Clinica
        if (!HorarioClinica.estaDentroDeHorario(request.fechaHora)) {
            throw HorarioNoDisponibleException("La hora solicitada ${request.fechaHora} está fuera del horario de atención.")
        }

        // 2. Obtener Servicio y Validar Duración
        val servicio = servicioMedicoRepository.findById(request.servicioId)
            .orElseThrow { NoSuchElementException("Servicio no encontrado con ID: ${request.servicioId}") }

        // 3. Validar Disponibilidad (Tetris Check)
        // NOTA: Usamos una consulta simple para ver si choca con alguna cita existente.
        // (StartA < EndB) and (EndA > StartB)
        // EndA = request.fechaHora + servicio.duracion
        val finSolicitado = request.fechaHora.plusMinutes(servicio.duracionMinutos.toLong())

        // Como CitaRepository.findCitasActivasEntre busca por rango del día, podemos hacer una query específica
        // o reusar lógica. Para eficiencia, hacemos check directo.
        val citasDelDia = citaRepository.findCitasActivasEntre(
            request.fechaHora.toLocalDate().atStartOfDay(),
            request.fechaHora.toLocalDate().plusDays(1).atStartOfDay()
        )

        val choca = citasDelDia.any { citaExistente ->
            val inicioExistente = citaExistente.fechaHora
            val finExistente = inicioExistente.plusMinutes(citaExistente.servicioMedico.duracionMinutos.toLong())

            request.fechaHora.isBefore(finExistente) && finSolicitado.isAfter(inicioExistente)
        }

        if (choca) {
            throw HorarioNoDisponibleException("El horario ${request.fechaHora} ya se encuentra reservado o conflige con otra cita.")
        }

        // 4. Resolver Mascota y Usuario (Ghost User Logic)
        val mascota: Mascota = if (request.mascotaId != null) {
            mascotaRepository.findById(request.mascotaId)
                .orElseThrow { NoSuchElementException("Mascota no encontrada con ID: ${request.mascotaId}") }
        } else if (origen == OrigenCita.WHATSAPP_BOT && request.telefono != null) {
            resolverUsuarioProvisional(request.telefono, request.nombreContacto)
        } else {
            throw IllegalArgumentException("Debe proporcionar un ID de mascota o un teléfono válido para reserva por WhatsApp.")
        }

        // 5. Calcular Precio
        val pesoParaCalculo = request.pesoActual ?: mascota.pesoKg?.let { BigDecimal.valueOf(it) }
        val precio = calculadoraPrecioService.calcularPrecio(servicio, pesoParaCalculo)

        // 6. Guardar Cita
        val cita = Cita(
            fechaHora = request.fechaHora,
            estado = EstadoCita.PENDIENTE_PAGO,
            mascota = mascota,
            servicioMedico = servicio,
            precioFinal = precio,
            origen = origen
        )

        val citaGuardada = citaRepository.save(cita)

        // 7. Publicar Evento
        eventPublisher.publishEvent(ReservaCreadaEvent(citaGuardada))

        return citaGuardada
    }

    private fun resolverUsuarioProvisional(telefono: String, nombreContacto: String?): Mascota {
        // Buscar usuario por telefono
        var usuario = usuarioRepository.findByTelefono(telefono)

        if (usuario == null) {
            // Crear Usuario Fantasma
            usuario = Usuario(
                email = null, // Email opcional
                telefono = telefono,
                nombre = nombreContacto ?: "Cliente WhatsApp",
                esProvisional = true
            )
            usuario = usuarioRepository.save(usuario)
        }

        // Buscar si tiene mascotas, si no crear una genérica
        // Esto es una simplificación. Idealmente el bot preguntaría nombre de mascota.
        // Si tiene mascotas, tomamos la primera (o el bot debería haber mandado ID).
        // Si no tiene, creamos "Mascota de [Nombre]"
        // Como MascotaRepository no tiene método user-based fácil aquí, intentamos cargar relación o crear nueva.
        // Dado que es Lazy, mejor crear una nueva si es usuario nuevo, o buscar si ya existe.
        // Para simplificar "Ghost User", creamos una mascota placeholder si es la primera vez.

        // Hack: Check if usuario.roles is loaded? No, check via repository if possible or just create one.
        // Vamos a asumir que si es provisional y no mandó ID, creamos una mascota "Sin Nombre" o recuperamos.
        // Para MVP: Creamos mascota provisional siempre si no manda ID.

        val mascota = Mascota(
            nombre = "Mascota de ${usuario.nombre}",
            especie = Especie.PERRO, // Default, bot debería preguntar
            tutor = usuario
        )
        return mascotaRepository.save(mascota)
    }
}
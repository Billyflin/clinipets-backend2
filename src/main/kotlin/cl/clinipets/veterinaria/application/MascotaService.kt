package cl.clinipets.veterinaria.application

import cl.clinipets.veterinaria.api.MascotaClinicalUpdateRequest
import cl.clinipets.veterinaria.api.MascotaCreateRequest
import cl.clinipets.veterinaria.api.MascotaResponse
import cl.clinipets.veterinaria.api.MascotaUpdateRequest
import cl.clinipets.veterinaria.api.toResponse
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class MascotaService(
    private val mascotaRepository: MascotaRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(MascotaService::class.java)

    @Transactional
    fun crear(request: MascotaCreateRequest, tutor: JwtPayload): MascotaResponse {
        logger.debug("[MASCOTA_SERVICE] Creando mascota: {} para tutor: {}", request.nombre, tutor.email)
        val user = userRepository.findById(tutor.userId)
            .orElseThrow { NotFoundException("Tutor no encontrado") }
        val mascota = mascotaRepository.save(
            Mascota(
                nombre = request.nombre,
                especie = request.especie,
                raza = request.raza,
                sexo = request.sexo,
                esterilizado = request.esterilizado,
                chipIdentificador = request.chipIdentificador,
                temperamento = request.temperamento,
                // Valores por defecto para permitir registro flexible
                pesoActual = request.pesoActual ?: -1.0,
                fechaNacimiento = request.fechaNacimiento ?: LocalDate.of(1900, 1, 1),
                tutor = user
            )
        )
        logger.info("[MASCOTA_SERVICE] Mascota creada exitosamente con ID: {}", mascota.id)
        return mascota.toResponse()
    }

    @Transactional(readOnly = true)
    fun listar(tutor: JwtPayload): List<MascotaResponse> =
        mascotaRepository.findAllByTutorId(tutor.userId).map { it.toResponse() }

    @Transactional(readOnly = true)
    fun obtener(id: UUID, tutor: JwtPayload): MascotaResponse =
        findMascotaDeTutor(id, tutor).toResponse()

    @Transactional
    fun actualizar(id: UUID, request: MascotaUpdateRequest, tutor: JwtPayload): MascotaResponse {
        logger.debug("[MASCOTA_SERVICE] Actualizando mascota ID: {}", id)
        val mascota = findMascotaDeTutor(id, tutor)
        mascota.nombre = request.nombre
        mascota.pesoActual = request.pesoActual
        
        request.raza?.let { mascota.raza = it }
        request.sexo?.let { mascota.sexo = it }
        request.esterilizado?.let { mascota.esterilizado = it }
        request.chipIdentificador?.let { mascota.chipIdentificador = it }
        request.temperamento?.let { mascota.temperamento = it }
        
        val updated = mascotaRepository.save(mascota)
        logger.info("[MASCOTA_SERVICE] Mascota actualizada exitosamente")
        return updated.toResponse()
    }

    @Transactional
    fun actualizarDatosClinicos(id: UUID, request: MascotaClinicalUpdateRequest): MascotaResponse {
        logger.info("[MASCOTA_CLINICO] Actualizando datos clínicos mascota ID: {}", id)

        // No pasamos JwtPayload porque asumimos que la autorización ya fue validada en el Controller (Role STAFF/ADMIN)
        // y este método es de uso interno para la clínica.
        val mascota = mascotaRepository.findById(id)
            .orElseThrow { NotFoundException("Mascota no encontrada") }

        request.pesoActual?.let { mascota.pesoActual = it }
        request.esterilizado?.let { mascota.esterilizado = it }

        request.testRetroviralNegativo?.let {
            mascota.testRetroviralNegativo = it
            if (it) {
                mascota.fechaUltimoTestRetroviral = LocalDate.now()
            }
        }

        request.observaciones?.let { mascota.observacionesClinicas = it }

        val updated = mascotaRepository.save(mascota)
        logger.info("[MASCOTA_CLINICO] Datos clínicos actualizados exitosamente")
        return updated.toResponse()
    }

    @Transactional
    fun eliminar(id: UUID, tutor: JwtPayload) {
        logger.warn("[MASCOTA_SERVICE] Eliminando mascota ID: {}", id)
        val mascota = findMascotaDeTutor(id, tutor)
        mascotaRepository.delete(mascota)
        logger.info("[MASCOTA_SERVICE] Mascota eliminada")
    }

    private fun findMascotaDeTutor(id: UUID, requestingUser: JwtPayload): Mascota {
        val mascota = mascotaRepository.findById(id)
            .orElseThrow { NotFoundException("Mascota no encontrada") }
        
        val isOwner = mascota.tutor.id == requestingUser.userId
        val isStaffOrAdmin = requestingUser.role == UserRole.STAFF || requestingUser.role == UserRole.ADMIN

        if (!isOwner && !isStaffOrAdmin) {
            logger.warn("[MASCOTA_SERVICE] Acceso denegado a mascota {}. Usuario: {}", id, requestingUser.email)
            throw UnauthorizedException("No puedes acceder a esta mascota")
        }
        return mascota
    }
}
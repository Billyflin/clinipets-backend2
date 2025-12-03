package cl.clinipets.veterinaria.application

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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MascotaService(
    private val mascotaRepository: MascotaRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun crear(request: MascotaCreateRequest, tutor: JwtPayload): MascotaResponse {
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
                pesoActual = request.pesoActual,
                fechaNacimiento = request.fechaNacimiento,
                tutor = user
            )
        )
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
        val mascota = findMascotaDeTutor(id, tutor)
        mascota.nombre = request.nombre
        mascota.pesoActual = request.pesoActual
        
        request.raza?.let { mascota.raza = it }
        request.sexo?.let { mascota.sexo = it }
        request.esterilizado?.let { mascota.esterilizado = it }
        request.chipIdentificador?.let { mascota.chipIdentificador = it }
        request.temperamento?.let { mascota.temperamento = it }
        
        return mascotaRepository.save(mascota).toResponse()
    }

    @Transactional
    fun eliminar(id: UUID, tutor: JwtPayload) {
        val mascota = findMascotaDeTutor(id, tutor)
        mascotaRepository.delete(mascota)
    }

    private fun findMascotaDeTutor(id: UUID, tutor: JwtPayload): Mascota {
        val mascota = mascotaRepository.findById(id)
            .orElseThrow { NotFoundException("Mascota no encontrada") }
        if (mascota.tutor.id != tutor.userId) throw UnauthorizedException("No puedes acceder a esta mascota")
        return mascota
    }
}

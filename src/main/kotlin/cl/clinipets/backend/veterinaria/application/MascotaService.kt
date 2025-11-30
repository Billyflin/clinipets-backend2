package cl.clinipets.backend.veterinaria.application

import cl.clinipets.backend.veterinaria.api.MascotaCreateRequest
import cl.clinipets.backend.veterinaria.api.MascotaResponse
import cl.clinipets.backend.veterinaria.api.toResponse
import cl.clinipets.backend.veterinaria.domain.Mascota
import cl.clinipets.backend.veterinaria.domain.MascotaRepository
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.identity.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
}

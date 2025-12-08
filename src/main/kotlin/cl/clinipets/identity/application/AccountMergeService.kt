package cl.clinipets.identity.application

import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.veterinaria.domain.MascotaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountMergeService(
    private val userRepository: UserRepository,
    private val mascotaRepository: MascotaRepository
) {
    private val logger = LoggerFactory.getLogger(AccountMergeService::class.java)

    /**
     * Unifica cuentas duplicadas por teléfono, preservando mascotas/historial del source en el target.
     */
    @Transactional
    fun mergeUsers(source: User, target: User): User {
        if (source.id == target.id) return target
        logger.info("[MERGE] Unificando cuentas. Source={}, Target={}", source.id, target.id)
        val mascotas = mascotaRepository.findAllByTutorId(source.id!!)
        mascotas.forEach { it.tutor = target }
        mascotaRepository.saveAll(mascotas)
        userRepository.delete(source)
        return target
    }
}

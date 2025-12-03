package cl.clinipets.veterinaria.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MascotaRepository : JpaRepository<Mascota, UUID> {
    fun findAllByTutorId(tutorId: UUID): List<Mascota>
}

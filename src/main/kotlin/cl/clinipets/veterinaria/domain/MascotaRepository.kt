package cl.clinipets.veterinaria.domain

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MascotaRepository : JpaRepository<Mascota, UUID>, JpaSpecificationExecutor<Mascota> {
    @EntityGraph(attributePaths = ["tutor"])
    fun findAllByTutorId(tutorId: UUID): List<Mascota>
}

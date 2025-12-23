package cl.clinipets.veterinaria.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SignosVitalesRepository : JpaRepository<SignosVitales, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["mascota"])
    fun findAllByMascotaIdOrderByFechaDesc(mascotaId: UUID): List<SignosVitales>
}

package cl.clinipets.veterinaria.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface HitoMedicoRepository : JpaRepository<HitoMedico, UUID> {
    fun findAllByMascotaIdOrderByFechaDesc(mascotaId: UUID): List<HitoMedico>
}

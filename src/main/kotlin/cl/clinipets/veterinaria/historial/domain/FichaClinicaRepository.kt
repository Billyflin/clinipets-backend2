package cl.clinipets.veterinaria.historial.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FichaClinicaRepository : JpaRepository<FichaClinica, UUID> {
    fun findAllByMascotaIdOrderByFechaAtencionDesc(mascotaId: UUID, pageable: Pageable): Page<FichaClinica>
}

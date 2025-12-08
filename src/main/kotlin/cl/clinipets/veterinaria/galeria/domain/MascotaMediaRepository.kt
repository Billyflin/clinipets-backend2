package cl.clinipets.veterinaria.galeria.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MascotaMediaRepository : JpaRepository<MascotaMedia, UUID> {
    fun findAllByMascotaIdOrderByFechaSubidaDesc(mascotaId: UUID): List<MascotaMedia>
}

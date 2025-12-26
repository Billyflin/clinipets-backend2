package cl.clinipets.servicios.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface LoteInsumoRepository : JpaRepository<LoteInsumo, UUID> {

    /**
     * Busca lotes vigentes (no vencidos) con stock disponible, ordenados por fecha de vencimiento (FEFO).
     */
    @Query("SELECT l FROM LoteInsumo l WHERE l.insumo.id = :insumoId AND l.fechaVencimiento >= :fecha AND l.cantidadActual > 0 ORDER BY l.fechaVencimiento ASC")
    fun findVigentesOrderByVencimiento(insumoId: UUID, fecha: LocalDate = LocalDate.now()): List<LoteInsumo>

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LoteInsumo l WHERE l.id = :id")
    fun findByIdWithLock(id: UUID): LoteInsumo?
}

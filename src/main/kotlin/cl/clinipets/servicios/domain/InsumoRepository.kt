package cl.clinipets.servicios.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InsumoRepository : JpaRepository<Insumo, UUID> {
    
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Insumo i WHERE i.id = :id")
    fun findByIdWithLock(id: UUID): Insumo?

    @Query("SELECT i FROM Insumo i WHERE i.stockActual <= i.stockMinimo")
    fun findAlertas(): List<Insumo>
}
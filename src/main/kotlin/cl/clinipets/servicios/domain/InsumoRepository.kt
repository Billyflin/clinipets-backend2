package cl.clinipets.servicios.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import java.util.UUID

interface InsumoRepository : JpaRepository<Insumo, UUID> {
    @Query("SELECT i FROM Insumo i WHERE i.stockActual <= i.stockMinimo")
    fun findAlertas(): List<Insumo>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Insumo i WHERE i.id = :id")
    fun findByIdWithLock(id: UUID): Insumo?
}

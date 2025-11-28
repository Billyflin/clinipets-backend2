package cl.clinipets.backend.servicios.infraestructura

import cl.clinipets.backend.servicios.dominio.ReglaPrecio
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReglaPrecioRepository : JpaRepository<ReglaPrecio, Long>

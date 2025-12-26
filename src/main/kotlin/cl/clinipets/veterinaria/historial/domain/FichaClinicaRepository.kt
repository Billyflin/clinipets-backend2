package cl.clinipets.veterinaria.historial.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

interface FichaSummary {
    val id: UUID
    val fechaAtencion: Instant
    val motivoConsulta: String
    
    @get:Value("#{target.mascota.nombre}")
    val nombreMascota: String

    @get:Value("#{target.autor.email}")
    val nombreVeterinario: String
}

@Repository
interface FichaClinicaRepository : JpaRepository<FichaClinica, UUID> {
    fun findAllByMascotaIdOrderByFechaAtencionDesc(mascotaId: UUID, pageable: Pageable): Page<FichaClinica>
    
    fun findAllProjectedByMascotaIdOrderByFechaAtencionDesc(mascotaId: UUID, pageable: Pageable): Page<FichaSummary>

    fun findAllByMascotaIdOrderByFechaAtencionAsc(mascotaId: UUID): List<FichaClinica>
    fun findByCitaId(citaId: UUID): FichaClinica?
}

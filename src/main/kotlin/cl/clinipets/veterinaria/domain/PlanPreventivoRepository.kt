package cl.clinipets.veterinaria.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface PlanPreventivoRepository : JpaRepository<PlanPreventivo, UUID> {
    fun findAllByMascotaIdOrderByFechaAplicacionDesc(mascotaId: UUID): List<PlanPreventivo>

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PlanPreventivo p WHERE p.fechaRefuerzo BETWEEN :inicio AND :fin")
    fun findRecordatoriosPendientes(inicio: java.time.Instant, fin: java.time.Instant): List<PlanPreventivo>
}

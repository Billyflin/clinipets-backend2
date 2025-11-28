package cl.clinipets.backend.veterinaria.infraestructura

import cl.clinipets.backend.veterinaria.dominio.EventoMedico
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface EventoMedicoRepository : JpaRepository<EventoMedico, Long> {

    // Para ver la ficha de una mascota (lo más reciente primero)
    fun findByMascotaIdOrderByFechaDesc(mascotaId: UUID): List<EventoMedico>

    // CRÍTICO PARA N8N:
    // Encuentra eventos cuyo recordatorio ("toca vacuna") caiga en el rango dado.
    // n8n llamará esto todos los días a las 09:00 AM preguntando por los vencimientos de "mañana" o "hoy".
    fun findByFechaProximoEventoBetween(inicio: LocalDate, fin: LocalDate): List<EventoMedico>
}

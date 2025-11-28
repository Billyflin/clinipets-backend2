package cl.clinipets.backend.mascotas.infraestructura

import cl.clinipets.backend.mascotas.dominio.Mascota
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface MascotaRepository : JpaRepository<Mascota, UUID> {

    /**
     * Busca todas las mascotas de un tutor.
     * Carga EAGER (inmediata) de 'raza' y 'colores'.
     */
    @EntityGraph(value = "Mascota.withRazaAndColores") // <-- CORRECCIÓN
    fun findAllByTutorId(tutorId: UUID): List<Mascota>

    /**
     * Busca una mascota por ID.
     * Carga EAGER (inmediata) de 'raza' y 'colores'.
     * (Necesario para los endpoints de Detalle y Actualizar)
     */
    @EntityGraph(value = "Mascota.withRazaAndColores") // <-- CORRECCIÓN
    override fun findById(id: UUID): Optional<Mascota>
}
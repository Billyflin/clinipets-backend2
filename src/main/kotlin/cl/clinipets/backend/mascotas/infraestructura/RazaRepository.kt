package cl.clinipets.backend.mascotas.infraestructura


import cl.clinipets.backend.mascotas.dominio.Especie
import cl.clinipets.backend.mascotas.dominio.Raza
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface RazaRepository : JpaRepository<Raza, UUID> {
    fun findByNombreAndEspecie(nombre: String, especie: Especie): Optional<Raza>
}
package cl.clinipets.veterinaria.domain

import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object MascotaSpecifications {

    fun conNombre(nombre: String?): Specification<Mascota> = Specification { root, _, cb ->
        nombre?.let { cb.like(cb.lower(root.get("nombre")), "%${it.lowercase()}%") }
    }

    fun conEspecie(especie: Especie?): Specification<Mascota> = Specification { root, _, cb ->
        especie?.let { cb.equal(root.get<Especie>("especie"), it) }
    }

    fun conRaza(raza: String?): Specification<Mascota> = Specification { root, _, cb ->
        raza?.let { cb.like(cb.lower(root.get("raza")), "%${it.lowercase()}%") }
    }

    fun conEsterilizado(esterilizado: Boolean?): Specification<Mascota> = Specification { root, _, cb ->
        esterilizado?.let { cb.equal(root.get<Boolean>("esterilizado"), it) }
    }

    fun conTutor(tutorId: UUID?): Specification<Mascota> = Specification { root, _, cb ->
        tutorId?.let { cb.equal(root.get<UUID>("tutor").get<UUID>("id"), it) }
    }

    fun conChip(chip: String?): Specification<Mascota> = Specification { root, _, cb ->
        chip?.let { cb.equal(root.get<String>("chipIdentificador"), it) }
    }
}

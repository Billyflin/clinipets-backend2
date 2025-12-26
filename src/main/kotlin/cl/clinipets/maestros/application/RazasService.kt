package cl.clinipets.maestros.application

import cl.clinipets.veterinaria.domain.Especie
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class RazasService {
    private val logger = LoggerFactory.getLogger(RazasService::class.java)

    private val razasPerro = listOf(
        "Mestizo",
        "Labrador Retriever",
        "Pastor Alemán",
        "Golden Retriever",
        "Bulldog Francés",
        "Beagle",
        "Poodle (Caniche)",
        "Chihuahua",
        "Boxer",
        "Dachshund (Salchicha)",
        "Husky Siberiano",
        "Yorkshire Terrier"
    ).sorted()

    private val razasGato = listOf(
        "Mestizo",
        "Persa",
        "Siamés",
        "Maine Coon",
        "Ragdoll",
        "Bengala",
        "Sphynx (Esfinge)",
        "British Shorthair",
        "Angora",
        "Azul Ruso"
    ).sorted()

    @Cacheable("razas")
    fun getRazas(especie: Especie?): List<String> {
        logger.info("[CACHE_MISS] Cargando razas para especie: {}", especie ?: "TODAS")
        return when (especie) {
            Especie.PERRO -> razasPerro
            Especie.GATO -> razasGato
            null -> (razasPerro + razasGato).sorted().distinct()
        }
    }
}

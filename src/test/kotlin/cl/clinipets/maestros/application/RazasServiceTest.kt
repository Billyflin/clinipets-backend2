package cl.clinipets.maestros.application

import cl.clinipets.veterinaria.domain.Especie
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RazasServiceTest {

    private val razasService = RazasService()

    @Test
    fun `should return dog breeds`() {
        val razas = razasService.getRazas(Especie.PERRO)
        assertTrue(razas.contains("Mestizo"))
        assertTrue(razas.contains("Labrador Retriever"))
        assertFalse(razas.contains("Siamés"))
    }

    @Test
    fun `should return cat breeds`() {
        val razas = razasService.getRazas(Especie.GATO)
        assertTrue(razas.contains("Mestizo"))
        assertTrue(razas.contains("Siamés"))
        assertFalse(razas.contains("Labrador Retriever"))
    }

    @Test
    fun `should return all breeds when especie is null`() {
        val razas = razasService.getRazas(null)
        assertTrue(razas.contains("Labrador Retriever"))
        assertTrue(razas.contains("Siamés"))
        assertTrue(razas.contains("Mestizo"))
    }
}

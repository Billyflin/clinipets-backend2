package cl.clinipets.backend.nucleo.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaversineHelperTest {

    @Test
    fun `distancia entre puntos iguales es cero`() {
        val lat = -33.45
        val lon = -70.66
        val d = HaversineHelper.calcularDistanciaKm(lat, lon, lat, lon)
        assertEquals(0.0, d, 1e-9)
    }

    @Test
    fun `distancia Santiago-Temuco es aproximadamente 616 km`() {
        val stgoLat = -33.45
        val stgoLon = -70.66
        val temucoLat = -38.770809
        val temucoLon = -72.595369
        val d = HaversineHelper.calcularDistanciaKm(stgoLat, stgoLon, temucoLat, temucoLon)
        // Tolerancia razonable +/- 5 km
        assertTrue(d in 610.0..625.0, "Distancia esperada ~616 km, actual=$d")
    }
}


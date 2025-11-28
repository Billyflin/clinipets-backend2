package cl.clinipets.backend.nucleo.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HaversineHelper {

    private const val RADIO_TIERRA_KM = 6371.0

    /**
     * Calcula la distancia en KM entre dos puntos Lat/Lon (grados decimales) usando Haversine.
     * Robusto ante errores numéricos (clamps) y claro en pasos.
     */
    fun calcularDistanciaKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)

        var a = sinDLat * sinDLat + cos(rLat1) * cos(rLat2) * sinDLon * sinDLon
        // Clamp por estabilidad numérica
        if (a < 0.0) a = 0.0
        if (a > 1.0) a = 1.0

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return RADIO_TIERRA_KM * c
    }
}
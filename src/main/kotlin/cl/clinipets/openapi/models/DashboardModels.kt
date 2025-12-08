package cl.clinipets.openapi.models

import cl.clinipets.servicios.api.ServicioMedicoDto
import cl.clinipets.veterinaria.api.MascotaResponse

data class DashboardResponse(
    val saludo: String,
    val mensajeIa: String,
    val mascotas: List<MascotaResponse>,
    val serviciosDestacados: List<ServicioMedicoDto>,
    val todosLosServicios: List<ServicioMedicoDto>
)

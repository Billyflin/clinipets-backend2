package cl.clinipets.servicios.api

import cl.clinipets.AbstractControllerTest
import cl.clinipets.servicios.application.PromocionService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.*

class PromocionControllerTest : AbstractControllerTest() {

    @MockBean
    private lateinit var promocionService: PromocionService

    @Test
    fun `listar promociones requiere STAFF o ADMIN`() {
        mockMvc.perform(get("/api/v1/promociones"))
            .andExpect(status().isForbidden)

        whenever(promocionService.listarTodas()).thenReturn(emptyList())

        mockMvc.perform(
            get("/api/v1/promociones")
                .with(staffUser())
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `crear promocion funciona para admin`() {
        val request = PromocionCreateRequest(
            nombre = "Cyber Vet",
            fechaInicio = LocalDate.now(),
            fechaFin = LocalDate.now().plusDays(7),
            serviciosTriggerIds = emptySet(),
            beneficios = emptyList()
        )
        val response = PromocionResponse(
            id = UUID.randomUUID(),
            nombre = request.nombre,
            descripcion = null,
            fechaInicio = request.fechaInicio,
            fechaFin = request.fechaFin,
            diasPermitidos = null,
            activa = true,
            serviciosTrigger = emptyList(),
            beneficios = emptyList()
        )

        whenever(promocionService.crear(any())).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/promociones")
                .with(adminUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nombre").value("Cyber Vet"))
    }

    @Test
    fun `eliminar promocion funciona para staff`() {
        val id = UUID.randomUUID()

        mockMvc.perform(
            delete("/api/v1/promociones/$id")
                .with(staffUser())
        )
            .andExpect(status().isNoContent)
    }
}

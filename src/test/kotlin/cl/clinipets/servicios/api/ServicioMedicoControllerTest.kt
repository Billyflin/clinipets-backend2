package cl.clinipets.servicios.api

import cl.clinipets.AbstractControllerTest
import cl.clinipets.servicios.application.ServicioMedicoService
import cl.clinipets.servicios.domain.CategoriaServicio
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.*

class ServicioMedicoControllerTest : AbstractControllerTest() {

    @MockBean
    private lateinit var servicioMedicoService: ServicioMedicoService

    @Test
    fun `listar servicios activos no requiere autenticacion`() {
        val dto = ServicioMedicoDto(
            id = UUID.randomUUID(),
            nombre = "Consulta General",
            precioBase = BigDecimal("25000"),
            precioAbono = null,
            requierePeso = false,
            duracionMinutos = 30,
            activo = true,
            categoria = CategoriaServicio.CONSULTA,
            especiesPermitidas = emptySet(),
            stock = null,
            bloqueadoSiEsterilizado = false,
            serviciosRequeridosIds = emptySet(),
            reglas = emptyList(),
            insumos = emptyList(),
            actualizaMarcador = null,
            condicionMarcadorClave = null,
            condicionMarcadorValor = null
        )
        whenever(servicioMedicoService.listarActivos()).thenReturn(listOf(dto))

        mockMvc.perform(get("/api/v1/servicios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].nombre").value("Consulta General"))
    }

    @Test
    fun `crear servicio requiere rol STAFF o ADMIN`() {
        val request = ServicioCreateRequest(
            nombre = "Nuevo Servicio",
            precioBase = BigDecimal("15000"),
            requierePeso = false,
            duracionMinutos = 15,
            categoria = CategoriaServicio.VACUNA
        )

        // Sin auth -> 401/403 (dependiendo de la config de seguridad)
        mockMvc.perform(
            post("/api/v1/servicios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)

        // Con Client -> 403
        mockMvc.perform(
            post("/api/v1/servicios")
                .with(clientUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)

        // Con Staff -> 200 OK
        val response = ServicioMedicoDto(
            id = UUID.randomUUID(),
            nombre = request.nombre,
            precioBase = request.precioBase,
            precioAbono = null,
            requierePeso = request.requierePeso,
            duracionMinutos = request.duracionMinutos,
            activo = true,
            categoria = request.categoria,
            especiesPermitidas = emptySet(),
            stock = null,
            bloqueadoSiEsterilizado = false,
            serviciosRequeridosIds = emptySet(),
            reglas = emptyList(),
            insumos = emptyList(),
            actualizaMarcador = null,
            condicionMarcadorClave = null,
            condicionMarcadorValor = null
        )
        whenever(servicioMedicoService.crear(any())).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/servicios")
                .with(staffUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nombre").value("Nuevo Servicio"))
    }

    @Test
    fun `actualizar dependencias funciona para staff`() {
        val id = UUID.randomUUID()
        val dependencias = setOf(UUID.randomUUID())
        val response = ServicioMedicoDto(
            id = id,
            nombre = "Servicio con Dep",
            precioBase = BigDecimal.ZERO,
            precioAbono = null,
            requierePeso = false,
            duracionMinutos = 30,
            activo = true,
            categoria = CategoriaServicio.OTRO,
            especiesPermitidas = emptySet(),
            stock = null,
            bloqueadoSiEsterilizado = false,
            serviciosRequeridosIds = dependencias,
            reglas = emptyList(),
            insumos = emptyList(),
            actualizaMarcador = null,
            condicionMarcadorClave = null,
            condicionMarcadorValor = null
        )

        whenever(servicioMedicoService.actualizarDependencias(any(), any())).thenReturn(response)

        mockMvc.perform(
            put("/api/v1/servicios/$id/dependencias")
                .with(staffUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dependencias))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `agregar regla de precio funciona para admin`() {
        val id = UUID.randomUUID()
        val request = ReglaPrecioRequest(0.0, 10.0, BigDecimal("20000"))
        val response = ServicioMedicoDto(
            id = id,
            nombre = "Servicio",
            precioBase = BigDecimal.ZERO,
            precioAbono = null,
            requierePeso = false,
            duracionMinutos = 30,
            activo = true,
            categoria = CategoriaServicio.CONSULTA,
            especiesPermitidas = emptySet(),
            stock = null,
            bloqueadoSiEsterilizado = false,
            serviciosRequeridosIds = emptySet(),
            reglas = emptyList(),
            insumos = emptyList(),
            actualizaMarcador = null,
            condicionMarcadorClave = null,
            condicionMarcadorValor = null
        )

        whenever(servicioMedicoService.agregarRegla(any(), any())).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/servicios/$id/reglas-precio")
                .with(adminUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
    }
}

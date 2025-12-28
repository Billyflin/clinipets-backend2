package cl.clinipets.servicios.api

import cl.clinipets.AbstractControllerTest
import cl.clinipets.servicios.application.InventarioReportService
import cl.clinipets.servicios.application.InventarioService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.*

class InventarioControllerTest : AbstractControllerTest() {

    @MockBean
    private lateinit var inventarioService: InventarioService

    @MockBean
    private lateinit var inventarioReportService: InventarioReportService

    @Test
    fun `listar insumos requiere STAFF o ADMIN`() {
        mockMvc.perform(get("/api/v1/inventario/insumos"))
            .andExpect(status().isForbidden)

        whenever(inventarioService.listarInsumos()).thenReturn(emptyList())

        mockMvc.perform(
            get("/api/v1/inventario/insumos")
                .with(staffUser())
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `crear insumo funciona para staff`() {
        val request = InsumoCreateRequest("Gasas", 10.0, "Unidades")
        val response = InsumoResponse(
            id = UUID.randomUUID(),
            nombre = request.nombre,
            stockActual = 0.0,
            stockMinimo = request.stockMinimo,
            unidadMedida = request.unidadMedida,
            contraindicacionMarcador = null,
            lotes = emptyList()
        )

        whenever(inventarioService.crearInsumo(any())).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/inventario/insumos")
                .with(staffUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nombre").value("Gasas"))
    }

    @Test
    fun `obtener alertas de stock funciona para staff`() {
        whenever(inventarioReportService.generarAlertasStock()).thenReturn(emptyList())

        mockMvc.perform(
            get("/api/v1/inventario/alertas")
                .with(staffUser())
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `obtener dashboard de vencimientos funciona para staff`() {
        whenever(inventarioService.listarInsumos()).thenReturn(emptyList())

        mockMvc.perform(
            get("/api/v1/inventario/vencimientos")
                .with(staffUser())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proximos_7_dias").isArray)
    }

    @Test
    fun `agregar lote funciona para admin`() {
        val id = UUID.randomUUID()
        val request = LoteCreateRequest("LOTE-001", LocalDate.now().plusMonths(6), 100.0)
        val response = InsumoResponse(
            id = id,
            nombre = "Insumo",
            stockActual = 100.0,
            stockMinimo = 10.0,
            unidadMedida = "U",
            contraindicacionMarcador = null,
            lotes = emptyList()
        )

        whenever(inventarioService.agregarLote(any(), any())).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/inventario/insumos/$id/lotes")
                .with(adminUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
    }
}

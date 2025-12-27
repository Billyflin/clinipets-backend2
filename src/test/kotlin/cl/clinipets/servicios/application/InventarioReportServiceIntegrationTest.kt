package cl.clinipets.servicios.application

import cl.clinipets.AbstractIntegrationTest
import cl.clinipets.servicios.domain.Insumo
import cl.clinipets.servicios.domain.InsumoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventarioReportServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var inventarioReportService: InventarioReportService

    @Autowired
    private lateinit var insumoRepository: InsumoRepository

    @Test
    fun `should generate stock alerts for items below minimum stock`() {
        // 1. Setup: Item below minimum
        insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Insumo Bajo", stockActual = 2.0, stockMinimo = 5.0, unidadMedida = "Units"
            )
        )

        // 2. Item at minimum (should also alert)
        insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Insumo Critico", stockActual = 10.0, stockMinimo = 10.0, unidadMedida = "Units"
            )
        )

        // 3. Item above minimum (should NOT alert)
        insumoRepository.saveAndFlush(
            Insumo(
                nombre = "Insumo OK", stockActual = 20.0, stockMinimo = 5.0, unidadMedida = "Units"
            )
        )

        val alertas = inventarioReportService.generarAlertasStock()

        assertEquals(2, alertas.size)
        assertTrue(alertas.any { it.nombre == "Insumo Bajo" })
        assertTrue(alertas.any { it.nombre == "Insumo Critico" })
        assertTrue(alertas.none { it.nombre == "Insumo OK" })
    }
}

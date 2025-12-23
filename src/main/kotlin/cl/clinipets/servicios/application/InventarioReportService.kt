package cl.clinipets.servicios.application

import cl.clinipets.servicios.api.InsumoDetalladoDto
import cl.clinipets.servicios.api.toDetalladoDto
import cl.clinipets.servicios.domain.InsumoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventarioReportService(
    private val insumoRepository: InsumoRepository
) {
    @Transactional(readOnly = true)
    fun generarAlertasStock(): List<InsumoDetalladoDto> {
        return insumoRepository.findAlertas().map { it.toDetalladoDto() }
    }
}

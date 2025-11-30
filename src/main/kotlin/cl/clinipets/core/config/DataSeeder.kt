package cl.clinipets.core.config

import cl.clinipets.backend.servicios.domain.ReglaPrecio
import cl.clinipets.backend.servicios.domain.ServicioMedico
import cl.clinipets.backend.servicios.domain.ServicioMedicoRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class DataSeeder(
    private val servicioMedicoRepository: ServicioMedicoRepository
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments?) {
        if (servicioMedicoRepository.count() > 0) return

        val consulta = ServicioMedico(
            nombre = "Consulta General",
            precioBase = 8000,
            requierePeso = false,
            duracionMinutos = 30,
            activo = true
        )

        val esterilizacion = ServicioMedico(
            nombre = "Esterilización Canina",
            precioBase = 30000, // base mínima
            requierePeso = true,
            duracionMinutos = 60,
            activo = true
        )
        val reglasEsterilizacion = listOf(
            ReglaPrecio(
                pesoMin = BigDecimal("0.0"),
                pesoMax = BigDecimal("10.0"),
                precio = 30000,
                servicio = esterilizacion
            ),
            ReglaPrecio(
                pesoMin = BigDecimal("10.0"),
                pesoMax = BigDecimal("15.0"),
                precio = 34000,
                servicio = esterilizacion
            ),
            ReglaPrecio(
                pesoMin = BigDecimal("15.0"),
                pesoMax = BigDecimal("20.0"),
                precio = 38000,
                servicio = esterilizacion
            ),
            ReglaPrecio(
                pesoMin = BigDecimal("20.0"),
                pesoMax = BigDecimal("30.0"),
                precio = 45000,
                servicio = esterilizacion
            )
        )
        esterilizacion.reglas.addAll(reglasEsterilizacion)

        val vacunas = ServicioMedico(
            nombre = "Vacunas",
            precioBase = 12000,
            requierePeso = false,
            duracionMinutos = 15,
            activo = true
        )

        servicioMedicoRepository.saveAll(listOf(consulta, esterilizacion, vacunas))
    }
}

package cl.clinipets.core.config

import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Especie
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
            precioBase = 15000,
            precioAbono = 3000,
            requierePeso = false,
            duracionMinutos = 30,
            activo = true,
            categoria = CategoriaServicio.CONSULTA,
            especiesPermitidas = mutableSetOf() // Todas
        )

        val esterilizacion = ServicioMedico(
            nombre = "Esterilización Canina",
            precioBase = 45000, // base mínima
            precioAbono = 5000,
            requierePeso = true,
            duracionMinutos = 60,
            activo = true,
            categoria = CategoriaServicio.CIRUGIA,
            especiesPermitidas = mutableSetOf(Especie.PERRO)
        )
        val reglasEsterilizacion = listOf(
            ReglaPrecio(
                pesoMin = BigDecimal("0.0"),
                pesoMax = BigDecimal("10.0"),
                precio = 45000,
                servicio = esterilizacion
            ),
            ReglaPrecio(
                pesoMin = BigDecimal("10.0"),
                pesoMax = BigDecimal("15.0"),
                precio = 50000,
                servicio = esterilizacion
            ),
            ReglaPrecio(
                pesoMin = BigDecimal("15.0"),
                pesoMax = BigDecimal("20.0"),
                precio = 55000,
                servicio = esterilizacion
            ),
            ReglaPrecio(
                pesoMin = BigDecimal("20.0"),
                pesoMax = BigDecimal("30.0"),
                precio = 65000,
                servicio = esterilizacion
            )
        )
        esterilizacion.reglas.addAll(reglasEsterilizacion)

        val vacunas = ServicioMedico(
            nombre = "Vacunas",
            precioBase = 12000,
            precioAbono = 2000,
            requierePeso = false,
            duracionMinutos = 15,
            activo = true,
            categoria = CategoriaServicio.VACUNA,
            especiesPermitidas = mutableSetOf(), // Todas
            stock = 100
        )

        val churu = ServicioMedico(
            nombre = "Churu Atún",
            precioBase = 3000,
            requierePeso = false,
            duracionMinutos = 0,
            activo = true,
            categoria = CategoriaServicio.PRODUCTO,
            especiesPermitidas = mutableSetOf(Especie.GATO),
            stock = 50
        )

        val servicioTest = ServicioMedico(
            nombre = "Test Flujo Pagos",
            precioBase = 2,
            precioAbono = 1,
            requierePeso = false,
            duracionMinutos = 15,
            activo = true,
            categoria = CategoriaServicio.OTRO,
            especiesPermitidas = mutableSetOf() // Todas
        )

        servicioMedicoRepository.saveAll(listOf(consulta, esterilizacion, vacunas, churu, servicioTest))
    }
}

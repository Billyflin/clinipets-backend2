package cl.clinipets.backend.servicios.aplicacion

import cl.clinipets.backend.servicios.dominio.ReglaPrecio
import cl.clinipets.backend.servicios.dominio.ServicioMedico
import cl.clinipets.backend.servicios.infraestructura.ServicioMedicoRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class ServiciosSeeder(
    private val servicioRepository: ServicioMedicoRepository
) {

    @PostConstruct
    @Transactional
    fun seed() {
        if (servicioRepository.count() > 0) {
            return
        }

        crearSiNoExiste("Consulta Primaria") {
            ServicioMedico(
                nombre = "Consulta Primaria",
                precioBase = 8000,
                requierePeso = false,
                duracionMinutos = 30,
                descripcion = "Atención primaria, incluye revisión general."
            )
        }

        crearSiNoExiste("Vacunación") {
            ServicioMedico(
                nombre = "Vacunación",
                precioBase = 15000,
                requierePeso = false,
                duracionMinutos = 15,
                descripcion = "Administración de vacunas anuales."
            )
        }

        crearSiNoExiste("Esterilización Canina") {
            val servicio = ServicioMedico(
                nombre = "Esterilización Canina",
                precioBase = 0,
                requierePeso = true,
                duracionMinutos = 60
            )
            // 0.0 - 10.0 kg -> $30.000
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("0.0"),
                    pesoMax = BigDecimal("10.0"),
                    precio = 30000
                )
            )
            // 10.1 - 15.0 kg -> $34.000
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("10.1"),
                    pesoMax = BigDecimal("15.0"),
                    precio = 34000
                )
            )
            // 15.1 - 20.0 kg -> $38.000
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("15.1"),
                    pesoMax = BigDecimal("20.0"),
                    precio = 38000
                )
            )
            // 20.1 - 25.0 kg -> $42.000
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("20.1"),
                    pesoMax = BigDecimal("25.0"),
                    precio = 42000
                )
            )
            // 25.1 - 30.0 kg -> $46.000
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("25.1"),
                    pesoMax = BigDecimal("30.0"),
                    precio = 46000
                )
            )
            // 30.1 - 35.0 kg -> $50.000
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("30.1"),
                    pesoMax = BigDecimal("35.0"),
                    precio = 50000
                )
            )
            // 35.1 - 40.0 kg -> $54.000
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("35.1"),
                    pesoMax = BigDecimal("40.0"),
                    precio = 54000
                )
            )
            servicio
        }

        crearSiNoExiste("Castración Felina Macho") {
            val servicio = ServicioMedico(
                nombre = "Castración Felina Macho",
                precioBase = 0,
                requierePeso = true,
                duracionMinutos = 60,
                descripcion = "Para gatos machos hasta 10kg"
            )
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("0.0"),
                    pesoMax = BigDecimal("10.0"),
                    precio = 25000
                )
            )
            servicio
        }

        crearSiNoExiste("Esterilización Felina Hembra") {
            val servicio = ServicioMedico(
                nombre = "Esterilización Felina Hembra",
                precioBase = 0,
                requierePeso = true,
                duracionMinutos = 60,
                descripcion = "Para gatas hembras hasta 10kg"
            )
            servicio.agregarRegla(
                ReglaPrecio(
                    pesoMin = BigDecimal("0.0"),
                    pesoMax = BigDecimal("10.0"),
                    precio = 30000
                )
            )
            servicio
        }

        crearSiNoExiste("Domicilio") {
            ServicioMedico(
                nombre = "Domicilio",
                precioBase = 3000,
                descripcion = "Recargo por visita a domicilio."
            )
        }
    }

    private fun crearSiNoExiste(nombre: String, creator: () -> ServicioMedico) {
        if (servicioRepository.findByNombre(nombre) == null) {
            servicioRepository.save(creator())
        }
    }
}

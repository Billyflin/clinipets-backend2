package cl.clinipets.agendamiento.domain

import cl.clinipets.core.web.BadRequestException
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Mascota
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ClinicalValidator(
    private val servicioMedicoRepository: ServicioMedicoRepository
) {

    private val logger = LoggerFactory.getLogger(ClinicalValidator::class.java)

    fun validarRequisitosClinicos(
        servicio: ServicioMedico,
        mascota: Mascota,
        serviciosEnCarritoIds: Set<UUID>
    ) {
        // 1. Regla de Esterilización (Legacy pero válida)
        if (servicio.bloqueadoSiEsterilizado && mascota.esterilizado) {
            throw BadRequestException("La mascota ${mascota.nombre} ya está esterilizada. No puede realizarse '${servicio.nombre}'.")
        }

        // 2. Regla de Marcadores Dinámicos (Clinical Intelligence)
        if (servicio.condicionMarcadorClave != null) {
            val clave = servicio.condicionMarcadorClave!!
            val valorRequerido = servicio.condicionMarcadorValor
            val valorActual = mascota.marcadores[clave]

            if (valorActual != valorRequerido) {
                // Si no tiene el valor requerido, verificamos si un servicio en el carrito va a actualizar este marcador
                val seraActualizado = serviciosEnCarritoIds.any { sid ->
                    val s = servicioMedicoRepository.findById(sid).orElse(null)
                    s?.actualizaMarcador == clave
                }

                if (!seraActualizado) {
                    val msg = if (valorActual == null) {
                        "El servicio '${servicio.nombre}' requiere un estado '$clave: $valorRequerido' que es desconocido."
                    } else {
                        "El servicio '${servicio.nombre}' está contraindicado: el marcador '$clave' es '$valorActual' y se requiere '$valorRequerido'."
                    }
                    throw BadRequestException(msg)
                }
            }
        }

        // 3. Regla de Dependencia de Servicios (Carrito)
        if (servicio.serviciosRequeridos.isEmpty()) return

        servicio.serviciosRequeridos.forEach { servicioReq ->
            val reqId = servicioReq.id!!
            if (!serviciosEnCarritoIds.contains(reqId)) {
                // Si el servicio requerido actualiza un marcador, y la mascota YA tiene el marcador OK, omitimos
                val claveQueActualiza = servicioReq.actualizaMarcador
                val cumplePorMarcador = if (claveQueActualiza != null) {
                    mascota.marcadores[claveQueActualiza] == servicio.condicionMarcadorValor
                } else false

                if (!cumplePorMarcador) {
                    throw BadRequestException("El servicio '${servicio.nombre}' requiere que agregues también: '${servicioReq.nombre}' (o que la mascota ya cumpla el requisito clínico).")
                }
            }
        }
    }
}

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
        if (servicio.bloqueadoSiEsterilizado && mascota.esterilizado) {
            throw BadRequestException("La mascota ${mascota.nombre} ya está esterilizada. No puede realizarse '${servicio.nombre}'.")
        }

        if (servicio.serviciosRequeridos.isEmpty()) return

        servicio.serviciosRequeridos.forEach { servicioReq ->
            val reqId = servicioReq.id!!
            if (!serviciosEnCarritoIds.contains(reqId)) {
                var cumpleClinicamente = false

                val nombreReq = servicioReq.nombre
                if (nombreReq.contains("Retroviral", ignoreCase = true) ||
                    nombreReq.contains("Leucemia", ignoreCase = true)
                ) {
                    if (mascota.testRetroviralNegativo) {
                        cumpleClinicamente = true
                        logger.info(">>> [VALIDACION] Dependencia '${servicioReq.nombre}' satisfecha por historial clínico (Negativo).")
                    }
                }

                if (!cumpleClinicamente) {
                    throw BadRequestException("El servicio '${servicio.nombre}' requiere que agregues también: '${servicioReq.nombre}' (o que la mascota ya cumpla el requisito clínico).")
                }
            }
        }
    }
}

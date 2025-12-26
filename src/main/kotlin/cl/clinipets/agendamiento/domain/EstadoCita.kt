package cl.clinipets.agendamiento.domain

import cl.clinipets.core.web.BadRequestException

enum class EstadoCita {
    CONFIRMADA,    // Reservada sin pagar
    EN_ATENCION,   // Vet atendiendo
    FINALIZADA,    // Pagada y stock descontado
    CANCELADA,     // Cancelada (stock devuelto)
    NO_ASISTIO     // No show (para estadísticas futuras)
}

/**
 * Máquina de estados que valida transiciones permitidas
 */
object EstadoCitaTransiciones {
    private val TRANSICIONES = mapOf(
        EstadoCita.CONFIRMADA to setOf(
            EstadoCita.EN_ATENCION,
            EstadoCita.CANCELADA,
            EstadoCita.NO_ASISTIO
        ),
        EstadoCita.EN_ATENCION to setOf(
            EstadoCita.FINALIZADA,
            EstadoCita.CANCELADA
        ),
        EstadoCita.FINALIZADA to emptySet(),
        EstadoCita.CANCELADA to emptySet(),
        EstadoCita.NO_ASISTIO to emptySet()
    )

    fun validarTransicion(desde: EstadoCita, hacia: EstadoCita) {
        if (TRANSICIONES[desde]?.contains(hacia) != true) {
            throw BadRequestException("No se puede cambiar de $desde a $hacia")
        }
    }
}
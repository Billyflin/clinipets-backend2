package cl.clinipets.agendamiento.domain

enum class EstadoCita {
    PENDIENTE_PAGO,
    POR_PAGAR, // Nuevo: abono pagado, falta saldo
    CONFIRMADA,
    EN_SALA,
    EN_ATENCION,
    FINALIZADA,
    CANCELADA
}
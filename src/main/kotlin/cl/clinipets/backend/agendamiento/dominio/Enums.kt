package cl.clinipets.backend.agendamiento.dominio

enum class EstadoCita {
    PENDIENTE_PAGO,
    CONFIRMADA,
    FINALIZADA,
    CANCELADA
}

enum class OrigenCita {
    APP_ANDROID,
    WHATSAPP_BOT,
    MANUAL
}

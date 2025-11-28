package cl.clinipets.backend.nucleo.eventos

import cl.clinipets.backend.agendamiento.dominio.eventos.ReservaCreadaEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class N8nWebhookListener(
    @Value("\${app.n8n.webhook-url:}") private val webhookUrl: String
) {

    private val logger = LoggerFactory.getLogger(N8nWebhookListener::class.java)
    private val restClient = RestClient.create()

    @Async
    @EventListener
    fun handleReservaCreada(event: ReservaCreadaEvent) {
        if (webhookUrl.isBlank()) {
            logger.warn("N8n Webhook URL not configured. Skipping notification.")
            return
        }

        val cita = event.cita
        val cliente = cita.mascota.tutor

        val payload = mapOf(
            "mensaje" to "Nueva reserva de ${cliente.nombre ?: "Cliente"}",
            "servicio" to cita.servicioMedico.nombre,
            "hora" to cita.fechaHora.toString(),
            "telefono" to (cliente.telefono ?: "Sin teléfono"),
            "email" to (cliente.email ?: "Sin email"),
            "precio" to cita.precioFinal,
            "origen" to cita.origen
        )

        try {
            restClient.post()
                .uri(webhookUrl)
                .body(payload)
                .retrieve()
                .toBodilessEntity()

            logger.info("Webhook enviado a N8n para cita ID: ${cita.id}")
        } catch (e: Exception) {
            logger.error("Error enviando webhook a N8n: ${e.message}", e)
        }
    }
}

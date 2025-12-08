package cl.clinipets.core.integration.meta

import cl.clinipets.core.ia.AgentResponse
import cl.clinipets.core.ia.VeterinaryAgentService
import cl.clinipets.core.integration.meta.dto.WebhookObject
import cl.clinipets.identity.application.OtpService
import cl.clinipets.identity.domain.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class MetaService(
    private val veterinaryAgentService: VeterinaryAgentService,
    private val whatsAppClient: WhatsAppClient,
    private val otpService: OtpService,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(MetaService::class.java)

    @Async
    fun processWebhook(payload: WebhookObject) {
        try {
            payload.entry.forEach { entry ->
                entry.changes.forEach { change ->
                    val value = change.value
                    if (value != null) {
                        // Procesar Mensajes Entrantes
                        value.messages?.forEach { message ->
                            val userPhone = message.from
                            var userMessage: String? = null

                            // Caso 1: Texto simple
                            if (message.type == "text" && message.text != null) {
                                userMessage = message.text.body
                            }
                            // Caso 2: Respuesta Interactiva (Botón o Lista)
                            else if (message.type == "interactive" && message.interactive != null) {
                                val interactive = message.interactive
                                val replyId = interactive.listReply?.id ?: interactive.buttonReply?.id
                                val replyTitle = interactive.listReply?.title ?: interactive.buttonReply?.title

                                if (replyId != null && replyId.startsWith("RES_")) {
                                    // Simulamos que el usuario escribió su elección explícitamente para que la IA entienda
                                    userMessage = "El usuario seleccionó la hora ID: $replyId ($replyTitle)"
                                    logger.info("[META_WEBHOOK] Interacción recibida de $userPhone: $replyId")
                                }
                            }

                            if (userMessage != null) {
                                logger.info("[META_WEBHOOK] Procesando mensaje de $userPhone: $userMessage")
                                procesarYResponder(userPhone, userMessage)
                            } else {
                                logger.debug("[META_WEBHOOK] Mensaje recibido tipo: ${message.type} (Ignorado o sin contenido procesable)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[META_WEBHOOK] Error procesando payload", e)
        }
    }

    private fun procesarYResponder(userPhone: String, userMessage: String) {
        val msgTrimmed = userMessage.trim()

        // 1. FAST-PATH: Detección de OTP (6 dígitos exactos)
        // Usamos all { it.isDigit() } para evitar problemas con regex y escapar caracteres.
        if (msgTrimmed.length == 6 && msgTrimmed.all { it.isDigit() }) {
            manejarOtpDirecto(userPhone, msgTrimmed)
            return
        }

        // 2. Flujo Normal: Llamar al Agente IA
        val respuestaIa = veterinaryAgentService.procesarMensaje(userPhone, userMessage)
        logger.warn("[DEBUG_PAGO] MetaService recibió respuesta del Agente: {}", respuestaIa)

        // Responder al usuario según tipo
        when (respuestaIa) {
            is AgentResponse.Text -> {
                logger.warn(
                    "[DEBUG_PAGO] Es tipo Texto. Content: '{}', PaymentURL: '{}'",
                    respuestaIa.content,
                    respuestaIa.paymentUrl
                )
                whatsAppClient.enviarMensaje(userPhone, respuestaIa.content)
                val paymentUrl = respuestaIa.paymentUrl
                if (paymentUrl != null) {
                    logger.warn("[DEBUG_PAGO] Intentando enviar mensaje extra con link...")
                    whatsAppClient.enviarMensaje(
                        userPhone,
                        "Para confirmar tu cita, realiza el abono aquí: $paymentUrl"
                    )
                }
            }

            is AgentResponse.ListOptions -> {
                whatsAppClient.enviarLista(
                    destinatario = userPhone,
                    texto = respuestaIa.text,
                    botonMenu = respuestaIa.buttonLabel,
                    opciones = respuestaIa.options
                )
            }
        }
    }

    private fun manejarOtpDirecto(telefono: String, codigo: String) {
        logger.info("[META_OTP] Detectado posible código OTP ($codigo) para $telefono. Procesando directamente.")
        try {
            otpService.validateOtp(telefono, codigo)

            // Actualizar estado verificado del usuario
            val normalized = otpService.normalizePhone(telefono)
            userRepository.findByPhone(normalized)?.let {
                it.phoneVerified = true
                userRepository.save(it)
            }

            whatsAppClient.enviarMensaje(
                telefono,
                "✅ ¡Código verificado correctamente! Tu teléfono ha sido autenticado."
            )
        } catch (e: Exception) {
            logger.warn("[META_OTP] Falló validación directa de OTP: ${e.message}")
            // Mensaje amigable de error
            val msgError = if (e.message?.contains("intentos") == true) {
                "❌ ${e.message}"
            } else {
                "❌ Código incorrecto o expirado. Por favor intenta solicitar uno nuevo."
            }
            whatsAppClient.enviarMensaje(telefono, msgError)
        }
    }
}

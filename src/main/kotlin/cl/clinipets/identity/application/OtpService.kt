package cl.clinipets.identity.application

import cl.clinipets.core.integration.meta.WhatsAppClient
import cl.clinipets.core.web.BadRequestException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.OtpToken
import cl.clinipets.identity.domain.OtpTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class OtpService(
    private val otpTokenRepository: OtpTokenRepository,
    private val whatsAppClient: WhatsAppClient
) {
    private val logger = LoggerFactory.getLogger(OtpService::class.java)
    private val random = SecureRandom()

    fun normalizePhone(phone: String): String =
        phone.replace(Regex("[^0-9]"), "")
            .removePrefix("56")
            .removePrefix("0")
            .let { if (it.startsWith("9")) it else "9$it" }
            .let { "56$it" }

    @Transactional
    fun requestOtp(rawPhone: String, purpose: String = "login"): String {
        val phone = normalizePhone(rawPhone)
        val now = Instant.now()

        otpTokenRepository.deleteByExpiresAtBefore(now)

        val last = otpTokenRepository.findTopByPhoneOrderByCreatedAtDesc(phone)
        if (last != null && Duration.between(last.updatedAt, now) < Duration.ofSeconds(60)) {
            throw BadRequestException("Por favor espera 1 minuto antes de solicitar otro código.")
        }

        val code = generateCode()
        val expires = now.plus(5, ChronoUnit.MINUTES)

        val token = otpTokenRepository.findByPhoneAndPurpose(phone, purpose)
        if (token != null) {
            token.code = code
            token.expiresAt = expires
            token.attempts = 0
            token.used = false
            token.updatedAt = now
            otpTokenRepository.save(token)
        } else {
            otpTokenRepository.save(
                OtpToken(
                    phone = phone,
                    code = code,
                    purpose = purpose,
                    expiresAt = expires,
                    attempts = 0,
                    used = false,
                    updatedAt = now
                )
            )
        }

        logger.info("[OTP] Código {} generado para {} (purpose={})", code, phone, purpose)
        whatsAppClient.enviarMensaje(phone, "🔐 Tu código de acceso Clinipets es: *$code*. No lo compartas.")
        return code
    }

    @Transactional
    fun validateOtp(rawPhone: String, code: String, purpose: String = "login") {
        val phone = normalizePhone(rawPhone)
        val token = otpTokenRepository.findByPhoneAndPurpose(phone, purpose)
            ?: throw UnauthorizedException("OTP inválido")

        if (Instant.now().isAfter(token.expiresAt)) {
            otpTokenRepository.delete(token)
            throw UnauthorizedException("OTP expirado")
        }

        if (token.attempts >= 3) {
            otpTokenRepository.delete(token)
            throw UnauthorizedException("Has excedido el número de intentos. Solicita un nuevo código.")
        }

        if (token.code != code) {
            token.attempts += 1
            otpTokenRepository.save(token)
            val remaining = (3 - token.attempts).coerceAtLeast(0)
            throw BadRequestException("Código incorrecto. Te quedan $remaining intentos.")
        }

        token.used = true
        otpTokenRepository.save(token)
        otpTokenRepository.deleteByPhone(phone)
    }

    private fun generateCode(): String = (100000 + random.nextInt(900000)).toString()
}
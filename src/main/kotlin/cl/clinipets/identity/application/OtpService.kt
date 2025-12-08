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

    fun normalizePhone(phone: String): String {
        val normalized = phone.replace(Regex("[^0-9]"), "")
            .removePrefix("56")
            .removePrefix("0")
            .let { if (it.startsWith("9")) it else "9$it" }
            .let { "56$it" }
        logger.debug("[OTP] Normalizando teléfono: '{}' -> '{}'", phone, normalized)
        return normalized
    }

    @Transactional
    fun requestOtp(rawPhone: String, purpose: String = "login"): String {
        val phone = normalizePhone(rawPhone)
        logger.info("[OTP_REQUEST] Iniciando solicitud para rawPhone='{}', normalized='{}'", rawPhone, phone)
        val now = Instant.now()

        otpTokenRepository.deleteByExpiresAtBefore(now)

        val last = otpTokenRepository.findTopByPhoneOrderByCreatedAtDesc(phone)
        if (last != null) {
            val elapsed = Duration.between(last.updatedAt, now)
            logger.info(
                "[OTP_REQUEST] Token previo encontrado. UpdatedAt: {}, Elapsed: {}s",
                last.updatedAt,
                elapsed.seconds
            )
            if (elapsed < Duration.ofSeconds(60)) {
                logger.warn("[OTP_REQUEST] Solicitud bloqueada por frecuencia (60s) para {}", phone)
                throw BadRequestException("Por favor espera 1 minuto antes de solicitar otro código.")
            }
        } else {
            logger.info("[OTP_REQUEST] No se encontró token previo activo para {}", phone)
        }

        val code = generateCode()
        val expires = now.plus(5, ChronoUnit.MINUTES)

        val token = otpTokenRepository.findByPhoneAndPurpose(phone, purpose)
        if (token != null) {
            logger.info("[OTP_REQUEST] Reutilizando/Actualizando token existente (ID={}) para {}", token.id, phone)
            token.code = code
            token.expiresAt = expires
            token.attempts = 0
            token.used = false
            token.updatedAt = now
            otpTokenRepository.save(token)
        } else {
            logger.info("[OTP_REQUEST] Creando nuevo token para {}", phone)
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
        logger.info("[OTP_VALIDATE] Validando código '{}' para teléfono '{}' (normalized='{}')", code, rawPhone, phone)
        
        val token = otpTokenRepository.findByPhoneAndPurpose(phone, purpose)

        if (token == null) {
            logger.error("[OTP_VALIDATE] Token no encontrado en DB para phone='{}' y purpose='{}'", phone, purpose)
            throw UnauthorizedException("OTP inválido")
        }

        if (Instant.now().isAfter(token.expiresAt)) {
            logger.warn("[OTP_VALIDATE] Token expirado para {}. ExpiresAt: {}", phone, token.expiresAt)
            otpTokenRepository.delete(token)
            throw UnauthorizedException("OTP expirado")
        }

        if (token.attempts >= 3) {
            logger.warn("[OTP_VALIDATE] Exceso de intentos ({}) para {}", token.attempts, phone)
            otpTokenRepository.delete(token)
            throw UnauthorizedException("Has excedido el número de intentos. Solicita un nuevo código.")
        }

        if (token.code != code) {
            token.attempts += 1
            otpTokenRepository.save(token)
            val remaining = (3 - token.attempts).coerceAtLeast(0)
            logger.warn(
                "[OTP_VALIDATE] Código incorrecto para {}. Intento {}/3. Recibido: '{}', Esperado: '{}'",
                phone,
                token.attempts,
                code,
                token.code
            )
            throw BadRequestException("Código incorrecto. Te quedan $remaining intentos.")
        }

        logger.info("[OTP_VALIDATE] Validación exitosa para {}", phone)
        token.used = true
        otpTokenRepository.save(token)
        otpTokenRepository.deleteByPhone(phone)
    }

    private fun generateCode(): String = (100000 + random.nextInt(900000)).toString()
}
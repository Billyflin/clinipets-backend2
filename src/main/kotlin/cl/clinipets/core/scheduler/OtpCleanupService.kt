package cl.clinipets.core.scheduler

import cl.clinipets.identity.domain.OneTimePasswordRepository
import cl.clinipets.identity.domain.OtpTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@EnableScheduling
class OtpCleanupService(
    private val oneTimePasswordRepository: OneTimePasswordRepository,
    private val otpTokenRepository: OtpTokenRepository
) {
    private val logger = LoggerFactory.getLogger(OtpCleanupService::class.java)

    @Scheduled(cron = "0 0 * * * *")
    fun cleanOldOtps() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        oneTimePasswordRepository.deleteByExpiresAtBefore(cutoff)
        otpTokenRepository.deleteByExpiresAtBefore(cutoff)
        logger.info("[OTP_CLEANUP] Limpieza realizada para OTPs email y teléfono con expiresAt < {}", cutoff)
    }
}

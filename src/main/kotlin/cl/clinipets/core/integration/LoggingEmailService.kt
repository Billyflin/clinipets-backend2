package cl.clinipets.core.integration

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LoggingEmailService : EmailService {
    private val logger = LoggerFactory.getLogger(LoggingEmailService::class.java)
    override fun send(to: String, subject: String, body: String) {
        logger.info("[EMAIL_LOG] To: {}, Subject: {}, Body: {}", to, subject, body)
    }
}

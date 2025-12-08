package cl.clinipets.core.integration

interface EmailService {
    fun send(to: String, subject: String, body: String)
}

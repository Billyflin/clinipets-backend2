package cl.clinipets.backend.nucleo.auditoria

import cl.clinipets.backend.nucleo.api.TokenPayload
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.*

@Component("auditorAware")
class AuditorAwareJwt : AuditorAware<UUID> {
    override fun getCurrentAuditor(): Optional<UUID> {
        val auth = SecurityContextHolder.getContext().authentication ?: return Optional.empty()
        val payload = auth.principal as? TokenPayload ?: return Optional.empty()
        return Optional.ofNullable(payload.subject)
    }
}

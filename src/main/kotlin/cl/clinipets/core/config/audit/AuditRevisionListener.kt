package cl.clinipets.core.config.audit

import cl.clinipets.core.security.JwtPayload
import org.hibernate.envers.RevisionListener
import org.springframework.security.core.context.SecurityContextHolder

class AuditRevisionListener : RevisionListener {
    override fun newRevision(revisionEntity: Any) {
        val auditEntity = revisionEntity as AuditRevisionEntity
        val auth = SecurityContextHolder.getContext().authentication
        
        if (auth != null && auth.principal is JwtPayload) {
            val user = auth.principal as JwtPayload
            auditEntity.auditor = user.email
        } else {
            auditEntity.auditor = "system"
        }
    }
}

package cl.clinipets.core.config.audit

import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.envers.DefaultRevisionEntity
import org.hibernate.envers.RevisionEntity

@Entity
@RevisionEntity(AuditRevisionListener::class)
@Table(name = "revinfo_extended")
class AuditRevisionEntity : DefaultRevisionEntity() {
    var auditor: String? = null
}

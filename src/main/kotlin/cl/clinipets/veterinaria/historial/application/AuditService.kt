package cl.clinipets.veterinaria.historial.application

import cl.clinipets.core.config.audit.AuditRevisionEntity
import cl.clinipets.veterinaria.historial.domain.FichaClinica
import jakarta.persistence.EntityManager
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.query.AuditEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class FichaRevisionDto(
    val revisionId: Int,
    val fechaRevision: java.util.Date,
    val auditor: String?,
    val tipoRevision: String,
    val ficha: FichaClinica
)

@Service
class AuditService(private val entityManager: EntityManager) {

    @Transactional(readOnly = true)
    fun obtenerRevisionesFicha(fichaId: UUID): List<FichaRevisionDto> {
        val auditReader = AuditReaderFactory.get(entityManager)
        
        // Consultamos las revisiones de la entidad FichaClinica para el ID dado
        val query = auditReader.createQuery()
            .forRevisionsOfEntity(FichaClinica::class.java, false, true)
            .add(AuditEntity.id().eq(fichaId))
            .addOrder(AuditEntity.revisionNumber().desc())

        val results = query.resultList as List<Array<Any>>

        return results.map { result ->
            val entity = result[0] as FichaClinica
            val revision = result[1] as AuditRevisionEntity
            val type = result[2] as org.hibernate.envers.RevisionType

            FichaRevisionDto(
                revisionId = revision.id,
                fechaRevision = revision.revisionDate,
                auditor = revision.auditor,
                tipoRevision = type.name,
                ficha = entity
            )
        }
    }
}

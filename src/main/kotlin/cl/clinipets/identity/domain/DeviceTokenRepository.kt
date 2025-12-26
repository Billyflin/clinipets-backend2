package cl.clinipets.identity.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeviceTokenRepository : JpaRepository<DeviceToken, UUID> {
    fun findAllByUserId(userId: UUID): List<DeviceToken>
    fun findByToken(token: String): DeviceToken?
    fun deleteByToken(token: String)
}

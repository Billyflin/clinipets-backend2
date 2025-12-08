package cl.clinipets.identity.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface OneTimePasswordRepository : JpaRepository<OneTimePassword, UUID> {
    fun findTopByEmailOrderByCreatedAtDesc(email: String): Optional<OneTimePassword>
    fun deleteByExpiresAtBefore(date: Instant)
}

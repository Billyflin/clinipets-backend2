package cl.clinipets.identity.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface OtpTokenRepository : JpaRepository<OtpToken, UUID> {
    fun findByPhoneAndPurpose(phone: String, purpose: String): OtpToken?
    fun findTopByPhoneOrderByCreatedAtDesc(phone: String): OtpToken?
    fun deleteByExpiresAtBefore(date: Instant)
    fun deleteByPhone(phone: String)
}

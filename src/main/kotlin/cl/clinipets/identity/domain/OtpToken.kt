package cl.clinipets.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "otp_tokens")
data class OtpToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, length = 20)
    val phone: String,

    @Column(nullable = false, length = 6)
    var code: String,

    @Column(nullable = false, length = 32)
    var purpose: String = "login",

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(nullable = false)
    val maxAttempts: Int = 3,

    @Column(nullable = false)
    var used: Boolean = false,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)

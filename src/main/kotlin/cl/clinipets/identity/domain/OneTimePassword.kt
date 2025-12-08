package cl.clinipets.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "one_time_passwords")
data class OneTimePassword(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, length = 255)
    val email: String,

    @Column(nullable = false, length = 6)
    val code: String,

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(nullable = false)
    val maxAttempts: Int = 3
)

package cl.clinipets.identity.domain

import cl.clinipets.core.domain.AuditableEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.util.*

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_email", columnList = "email")
    ]
)
@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    val passwordHash: String,

    var phone: String? = null,
    var address: String? = null,

    @Column(length = 1024)
    var photoUrl: String? = null,

    @Column(nullable = false)
    var phoneVerified: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var authProvider: AuthProvider = AuthProvider.OTP,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var role: UserRole = UserRole.CLIENT,
    
    // createdAt y deleted vienen de AuditableEntity
) : AuditableEntity()

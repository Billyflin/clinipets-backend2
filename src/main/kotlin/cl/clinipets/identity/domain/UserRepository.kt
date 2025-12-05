package cl.clinipets.identity.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailIgnoreCase(email: String): User?
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun findAllByRoleIn(roles: Collection<UserRole>): List<User>
}

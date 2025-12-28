package cl.clinipets

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import java.util.*

@AutoConfigureMockMvc
abstract class AbstractControllerTest : AbstractIntegrationTest() {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected fun adminUser() = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
        .jwt {
            it.claim("user_id", UUID.randomUUID().toString())
                .claim("email", "admin@test.com")
                .claim("role", "ADMIN")
        }

    protected fun staffUser() = jwt().authorities(SimpleGrantedAuthority("ROLE_STAFF"))
        .jwt {
            it.claim("user_id", UUID.randomUUID().toString())
                .claim("email", "staff@test.com")
                .claim("role", "STAFF")
        }

    protected fun clientUser() = jwt().authorities(SimpleGrantedAuthority("ROLE_CLIENT"))
        .jwt {
            it.claim("user_id", UUID.randomUUID().toString())
                .claim("email", "client@test.com")
                .claim("role", "CLIENT")
        }
}

package cl.clinipets

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
abstract class AbstractIntegrationTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("clinipets_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        private val minio = GenericContainer(DockerImageName.parse("minio/minio:latest")).apply {
            withExposedPorts(9000)
            withEnv("MINIO_ROOT_USER", "minioadmin")
            withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            withCommand("server /data")
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.PostgreSQLDialect" }

            registry.add("minio.url") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
            registry.add("minio.access-key") { "minioadmin" }
            registry.add("minio.secret-key") { "minioadmin" }
            registry.add("minio.bucket-name") { "test-bucket" }
        }
    }
}
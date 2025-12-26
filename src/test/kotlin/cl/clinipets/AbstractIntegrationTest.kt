package cl.clinipets

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
abstract class AbstractIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("clinipets_test")
            .withUsername("test")
            .withPassword("test")

        @Container
        val minio = GenericContainer("minio/minio:latest")
            .withEnv("MINIO_ACCESS_KEY", "test_access")
            .withEnv("MINIO_SECRET_KEY", "test_secret")
            .withCommand("server /data")
            .withExposedPorts(9000)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.PostgreSQLDialect" }
            
            val minioUrl = "http://${minio.host}:${minio.getMappedPort(9000)}"
            registry.add("minio.url") { minioUrl }
            registry.add("minio.access-key") { "test_access" }
            registry.add("minio.secret-key") { "test_secret" }
            registry.add("minio.bucket-name") { "test-bucket" }
            registry.add("storage.type") { "minio" }
        }
    }
}

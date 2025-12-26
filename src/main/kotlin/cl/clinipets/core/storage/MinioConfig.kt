package cl.clinipets.core.storage

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
@ConditionalOnProperty(name = ["storage.type"], havingValue = "minio")
class MinioConfig {

    private val logger = LoggerFactory.getLogger(MinioConfig::class.java)

    @Value("\${minio.url}")
    private lateinit var url: String

    @Value("\${minio.access-key}")
    private lateinit var accessKey: String

    @Value("\${minio.secret-key}")
    private lateinit var secretKey: String

    @Value("\${minio.bucket-name}")
    private lateinit var bucketName: String

    @Bean
    fun minioClient(): MinioClient {
        return MinioClient.builder()
            .endpoint(url)
            .credentials(accessKey, secretKey)
            .build()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun initBucket() {
        try {
            val client = minioClient()
            val exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
            if (!exists) {
                logger.info("[MINIO] Creando bucket: {}", bucketName)
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())

                // Configurar política pública de lectura (opcional, para que las URLs sean accesibles sin firmar)
                // En un entorno real estricto, esto podría omitirse y usar URLs firmadas.
                // Aquí lo dejamos "privado" por defecto, el servicio generará URLs o stream.
            } else {
                logger.info("[MINIO] Bucket '{}' ya existe.", bucketName)
            }
        } catch (e: Exception) {
            logger.error("[MINIO] Error inicializando bucket: {}", e.message)
        }
    }
}

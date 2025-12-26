package cl.clinipets.core.storage

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["storage.type"], havingValue = "minio")
class MinioStorageService(
    private val minioClient: MinioClient,
    @Value("\${minio.bucket-name}") private val bucketName: String,
    @Value("\${minio.url}") private val minioUrl: String
) : StorageService {
    private val logger = LoggerFactory.getLogger(MinioStorageService::class.java)

    override fun uploadFile(file: MultipartFile, folder: String): String {
        try {
            val originalFilename = file.originalFilename ?: "unknown"
            val extension = originalFilename.substringAfterLast('.', "")
            val fileName = "${UUID.randomUUID()}.$extension"
            val objectName = "$folder/$fileName"

            val contentType = file.contentType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(objectName)
                    .stream(file.inputStream, file.size, -1)
                    .contentType(contentType)
                    .build()
            )

            logger.info("[MINIO] Archivo subido: {}", objectName)
            return objectName
        } catch (e: Exception) {
            throw RuntimeException("Error al subir a MinIO: ${e.message}")
        }
    }

    override fun getFile(objectName: String): InputStream {
        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectName)
                .build()
        )
    }

    override fun deleteFile(objectName: String) {
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectName)
                .build()
        )
        logger.info("[MINIO] Archivo eliminado: {}", objectName)
    }

    override fun getUrl(objectName: String): String {
        // En una implementación real, esto podría generar una URL pre-firmada
        return "$minioUrl/$bucketName/$objectName"
    }
}

package cl.clinipets.core.storage

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["minio.enabled"], havingValue = "true", matchIfMissing = true)
class StorageService(
    private val minioClient: MinioClient,
    @Value("\${minio.bucket-name}") private val bucketName: String,
    @Value("\${minio.url}") private val minioUrl: String
) {
    private val logger = LoggerFactory.getLogger(StorageService::class.java)

    /**
     * Sube un archivo a MinIO y retorna el objectName (path relativo en el bucket).
     * @param file El archivo Multipart recibido del controlador.
     * @param folder Carpeta virtual (ej: "mascotas", "fichas").
     * @return El 'objectName' o identificador interno del archivo.
     */
    fun uploadFile(file: MultipartFile, folder: String): String {
        try {
            val originalFilename = file.originalFilename ?: "unknown"
            val extension = originalFilename.substringAfterLast('.', "")
            val fileName = "${UUID.randomUUID()}.$extension"
            val objectName = "$folder/$fileName"

            val contentType = file.contentType?.takeIf { it.isNotBlank() } ?: when (extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "pdf" -> "application/pdf"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }

            logger.info(
                "[STORAGE] Subiendo archivo: {} a bucket: {}/{} (Type: {})",
                originalFilename,
                bucketName,
                objectName,
                contentType
            )

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(objectName)
                    .stream(file.inputStream, file.size, -1)
                    .contentType(contentType)
                    .build()
            )

            logger.info("[STORAGE] Archivo subido exitosamente: {}", objectName)
            return objectName

        } catch (e: Exception) {
            logger.error("[STORAGE] Error subiendo archivo", e)
            throw RuntimeException("Error al subir archivo al almacenamiento: ${e.message}")
        }
    }

    /**
     * Obtiene el InputStream de un archivo almacenado en MinIO.
     * @param objectName El identificador del archivo (path dentro del bucket).
     * @return InputStream del archivo.
     */
    fun getFile(objectName: String): InputStream {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(objectName)
                    .build()
            )
        } catch (e: Exception) {
            logger.error("[STORAGE] Error obteniendo archivo: {}", objectName, e)
            throw RuntimeException("Error al obtener archivo del almacenamiento: ${e.message}")
        }
    }
}

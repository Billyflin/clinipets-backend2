package cl.clinipets.core.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["storage.type"], havingValue = "local", matchIfMissing = true)
class LocalStorageService(
    @Value("\${storage.local.path:uploads}") private val basePath: String,
    @Value("\${server.port:8080}") private val port: Int
) : StorageService {
    private val logger = LoggerFactory.getLogger(LocalStorageService::class.java)

    init {
        val directory = File(basePath)
        if (!directory.exists()) {
            directory.mkdirs()
            logger.info("[LOCAL-STORAGE] Directorio de carga creado en: {}", directory.absolutePath)
        }
    }

    override fun uploadFile(file: MultipartFile, folder: String): String {
        val folderPath = Paths.get(basePath, folder)
        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath)
        }

        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast('.', "")
        val fileName = "${UUID.randomUUID()}.$extension"
        val relativePath = "$folder/$fileName"
        
        val targetPath = folderPath.resolve(fileName)
        file.transferTo(targetPath.toFile())

        logger.info("[LOCAL-STORAGE] Archivo guardado en: {}", targetPath)
        return relativePath
    }

    override fun getFile(objectName: String): InputStream {
        val path = Paths.get(basePath, objectName)
        return Files.newInputStream(path)
    }

    override fun deleteFile(objectName: String) {
        val path = Paths.get(basePath, objectName)
        Files.deleteIfExists(path)
        logger.info("[LOCAL-STORAGE] Archivo eliminado: {}", objectName)
    }

    override fun getUrl(objectName: String): String {
        // En local, podrías mapear un ResourceHandler para servir estos archivos
        return "/api/public/storage/$objectName"
    }
}

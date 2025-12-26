package cl.clinipets.core.storage

import org.springframework.web.multipart.MultipartFile
import java.io.InputStream

interface StorageService {
    /**
     * Sube un archivo y retorna su identificador/path único.
     */
    fun uploadFile(file: MultipartFile, folder: String): String

    /**
     * Obtiene el flujo de datos del archivo.
     */
    fun getFile(objectName: String): InputStream

    /**
     * Elimina un archivo del almacenamiento.
     */
    fun deleteFile(objectName: String)

    /**
     * Genera una URL pública o temporal para acceder al archivo.
     */
    fun getUrl(objectName: String): String
}
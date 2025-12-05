package cl.clinipets.core.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.io.FileInputStream

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @PostConstruct
    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            return
        }

        val credentials = loadCredentials()
        if (credentials == null) {
            logger.warn("[FIREBASE] Credenciales no encontradas. Notificaciones push deshabilitadas.")
            return
        }

        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build()

        FirebaseApp.initializeApp(options)
        logger.info("[FIREBASE] Inicializado correctamente.")
    }

    private fun loadCredentials(): GoogleCredentials? {
        val envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS") ?: System.getenv("FIREBASE_CONFIG")
        if (!envPath.isNullOrBlank()) {
            val envFile = File(envPath)
            if (envFile.exists()) {
                FileInputStream(envFile).use {
                    logger.info("[FIREBASE] Cargando credenciales desde ruta de entorno: {}", envPath)
                    return GoogleCredentials.fromStream(it)
                }
            } else {
                logger.warn("[FIREBASE] Ruta de entorno {} no existe.", envPath)
            }
        }

        val classpathResource = ClassPathResource("firebase-service-account.json")
        if (classpathResource.exists()) {
            logger.info("[FIREBASE] Cargando credenciales desde classpath.")
            classpathResource.inputStream.use {
                return GoogleCredentials.fromStream(it)
            }
        }

        val file = File("firebase-service-account.json")
        if (file.exists()) {
            logger.info("[FIREBASE] Cargando credenciales desde el sistema de archivos.")
            FileInputStream(file).use {
                return GoogleCredentials.fromStream(it)
            }
        }

        return null
    }
}

package cl.clinipets.veterinaria.galeria.application

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.storage.StorageService
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.galeria.api.MediaResponse
import cl.clinipets.veterinaria.galeria.domain.MascotaMedia
import cl.clinipets.veterinaria.galeria.domain.MascotaMediaRepository
import cl.clinipets.veterinaria.galeria.domain.MediaType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.util.UUID

@Service
class GaleriaService(
    private val mascotaRepository: MascotaRepository,
    private val mascotaMediaRepository: MascotaMediaRepository,
    private val storageService: StorageService
) {
    private val logger = LoggerFactory.getLogger(GaleriaService::class.java)

    @Transactional
    fun subirArchivo(
        mascotaId: UUID,
        file: MultipartFile,
        titulo: String?,
        tipo: MediaType,
        principal: JwtPayload
    ): MediaResponse {
        val mascota = mascotaRepository.findById(mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada") }

        // Validar permisos: Staff o Dueño
        if (principal.role != UserRole.STAFF && mascota.tutor.id != principal.userId) {
            throw UnauthorizedException("No tienes permiso para subir archivos a esta mascota")
        }

        // Subir a Storage
        val folder = "mascotas/$mascotaId"
        val objectPath = storageService.uploadFile(file, folder)

        val media = mascotaMediaRepository.save(
            MascotaMedia(
                mascota = mascota,
                url = objectPath, // Guardamos el path relativo (objectName)
                tipo = tipo,
                titulo = titulo
            )
        )

        logger.info("[GALERIA] Archivo subido para mascota {}: {}", mascotaId, media.id)

        return MediaResponse(
            id = media.id!!,
            url = media.url,
            tipo = media.tipo,
            titulo = media.titulo,
            fechaSubida = media.fechaSubida
        )
    }

    @Transactional(readOnly = true)
    fun listarArchivos(mascotaId: UUID, principal: JwtPayload): List<MediaResponse> {
        val mascota = mascotaRepository.findById(mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada") }

        if (principal.role != UserRole.STAFF && mascota.tutor.id != principal.userId) {
            throw UnauthorizedException("No tienes permiso para ver la galería de esta mascota")
        }

        return mascotaMediaRepository.findAllByMascotaIdOrderByFechaSubidaDesc(mascotaId)
            .map {
                MediaResponse(
                    id = it.id!!,
                    url = it.url,
                    tipo = it.tipo,
                    titulo = it.titulo,
                    fechaSubida = it.fechaSubida
                )
            }
    }

    @Transactional(readOnly = true)
    fun descargarArchivo(mediaId: UUID, principal: JwtPayload): Pair<InputStream, String> {
        val media = mascotaMediaRepository.findById(mediaId)
            .orElseThrow { NotFoundException("Archivo no encontrado") }

        // Validar permisos
        val mascota = media.mascota
        if (principal.role != UserRole.STAFF && mascota.tutor.id != principal.userId) {
            throw UnauthorizedException("No tienes permiso para descargar este archivo")
        }

        val inputStream = storageService.getFile(media.url)

        // Deducir contentType básico
        val extension = media.url.substringAfterLast('.', "").lowercase()
        val contentType = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }

        return inputStream to contentType
    }
}

package cl.clinipets.backend.nucleo.errors

import cl.clinipets.backend.agendamiento.dominio.excepciones.HorarioNoDisponibleException
import cl.clinipets.backend.servicios.dominio.excepciones.PesoRequeridoException
import cl.clinipets.backend.servicios.dominio.excepciones.PrecioNoDefinidoException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    data class ErrorRespuesta(
        val tipo: String,
        val mensaje: String,
        val detalles: Map<String, Any> = emptyMap()
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ErrorRespuesta> {
        val errores = ex.bindingResult.allErrors.associate { error ->
            val campo = (error as FieldError).field
            val mensaje = error.defaultMessage ?: "Error de validación"
            campo to mensaje
        }

        return ResponseEntity.badRequest().body(
            ErrorRespuesta(
                tipo = "ERROR_VALIDACION",
                mensaje = "Error en los datos enviados",
                detalles = errores
            )
        )
    }

    @ExceptionHandler(PesoRequeridoException::class)
    fun handlePesoRequerido(ex: PesoRequeridoException): ResponseEntity<ErrorRespuesta> {
        return ResponseEntity.badRequest().body(
            ErrorRespuesta(
                tipo = "ERROR_NEGOCIO",
                mensaje = ex.message ?: "Peso requerido",
                detalles = mapOf("error" to "El servicio requiere que se especifique el peso de la mascota")
            )
        )
    }

    @ExceptionHandler(PrecioNoDefinidoException::class)
    fun handlePrecioNoDefinido(ex: PrecioNoDefinidoException): ResponseEntity<ErrorRespuesta> {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorRespuesta(
                tipo = "ERROR_NEGOCIO",
                mensaje = ex.message ?: "Precio no definido",
                detalles = mapOf("error" to "No hay regla de precio para el peso indicado")
            )
        )
    }

    @ExceptionHandler(HorarioNoDisponibleException::class)
    fun handleHorarioNoDisponible(ex: HorarioNoDisponibleException): ResponseEntity<ErrorRespuesta> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorRespuesta(
                tipo = "HORARIO_NO_DISPONIBLE",
                mensaje = ex.message ?: "Horario no disponible"
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorRespuesta> {
        return ResponseEntity.badRequest().body(
            ErrorRespuesta(
                tipo = "SOLICITUD_INVALIDA",
                mensaje = ex.message ?: "Solicitud inválida"
            )
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorRespuesta> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorRespuesta(
                tipo = "RECURSO_NO_ENCONTRADO",
                mensaje = ex.message ?: "No encontrado"
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorRespuesta> {
        ex.printStackTrace() // Log error ideally
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorRespuesta(
                tipo = "ERROR_INTERNO",
                mensaje = "Ha ocurrido un error inesperado: ${ex.message}"
            )
        )
    }
}


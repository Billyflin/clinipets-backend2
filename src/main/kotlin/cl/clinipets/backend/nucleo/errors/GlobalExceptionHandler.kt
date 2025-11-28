package cl.clinipets.backend.nucleo.errors

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ProblemDetail {
        val pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
        pd.title = "Solicitud inválida"
        pd.detail = ex.message
        return pd
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ProblemDetail {
        val pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        pd.title = "No encontrado"
        pd.detail = ex.message
        return pd
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException): ProblemDetail {
        val pd = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        pd.title = "Conflicto"
        pd.detail = ex.message
        return pd
    }

    @ExceptionHandler(IllegalAccessException::class)
    fun handleForbidden(ex: IllegalAccessException): ProblemDetail {
        val pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN)
        pd.title = "Acceso denegado"
        pd.detail = ex.message
        return pd
    }

    @ExceptionHandler(ErrorResponseException::class)
    fun handleErrorResponse(ex: ErrorResponseException): ProblemDetail {
        return ex.body
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ProblemDetail {
        val pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        pd.title = "Error interno del servidor"
        pd.detail = "Ha ocurrido un error inesperado" + (ex.message?.let { ": $it" } ?: "")
        return pd
    }
}


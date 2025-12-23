package cl.clinipets.core.web

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RestExceptionHandler {
    private val logger = LoggerFactory.getLogger(RestExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("NotFound: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.NOT_FOUND, ex, request)
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("Conflict: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.CONFLICT, ex, request)
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("Unauthorized: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.UNAUTHORIZED, ex, request)
    }

    @ExceptionHandler(BadRequestException::class, IllegalArgumentException::class)
    fun handleBadRequest(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("BadRequest: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.BAD_REQUEST, ex, request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val fieldError = ex.bindingResult.fieldError
        val message = fieldError?.defaultMessage ?: "Datos inválidos"
        logger.warn("Validation Error: {} [{}]", message, request.servletPath)
        return buildError(HttpStatus.BAD_REQUEST, IllegalArgumentException(message), request)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.error("Internal Server Error at [{}]: {}", request.servletPath, ex.message, ex)
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex, request)
    }

    private fun buildError(status: HttpStatus, ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        val apiError = ApiError(
            message = ex.message ?: status.reasonPhrase,
            status = status.value(),
            path = request.servletPath
        )
        return ResponseEntity.status(status).body(apiError)
    }
}

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
        return buildError(HttpStatus.NOT_FOUND, ex, request, "NOT_FOUND")
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("Conflict: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.CONFLICT, ex, request, "CONFLICT")
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("Unauthorized: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.UNAUTHORIZED, ex, request, "UNAUTHORIZED")
    }

    @ExceptionHandler(BadRequestException::class, IllegalArgumentException::class)
    fun handleBadRequest(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("BadRequest: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.BAD_REQUEST, ex, request, "BAD_REQUEST")
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDenied(
        ex: org.springframework.security.access.AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ApiError> {
        logger.warn("AccessDenied: {} [{}]", ex.message, request.servletPath)
        return buildError(HttpStatus.FORBIDDEN, ex, request, "FORBIDDEN")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val validationErrors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        val message = "Error de validación"
        logger.warn("Validation Error: {} [{}]", message, request.servletPath)
        return buildError(HttpStatus.BAD_REQUEST, ex, request, "VALIDATION_ERROR", validationErrors)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.error("Internal Server Error at [{}]: {}", request.servletPath, ex.message, ex)
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex, request, "INTERNAL_ERROR")
    }

    private fun buildError(
        status: HttpStatus,
        ex: Exception,
        request: HttpServletRequest,
        code: String,
        validationErrors: List<String>? = null
    ): ResponseEntity<ApiError> {
        val message = if (code == "VALIDATION_ERROR") "Error de validación" else (ex.message ?: status.reasonPhrase)
        val apiError = ApiError(
            message = message,
            code = code,
            status = status.value(),
            path = request.servletPath,
            validationErrors = validationErrors
        )
        return ResponseEntity.status(status).body(apiError)
    }
}
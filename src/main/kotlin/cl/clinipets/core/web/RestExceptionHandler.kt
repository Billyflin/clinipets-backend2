package cl.clinipets.core.web

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.Instant

@RestControllerAdvice
class RestExceptionHandler {
    private val logger = LoggerFactory.getLogger(RestExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException, request: HttpServletRequest): ProblemDetail {
        logger.warn("NotFound: {} [{}]", ex.message, request.servletPath)
        return buildProblemDetail(HttpStatus.NOT_FOUND, ex, request, "Not Found")
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest): ProblemDetail {
        logger.warn("Conflict: {} [{}]", ex.message, request.servletPath)
        return buildProblemDetail(HttpStatus.CONFLICT, ex, request, "Conflict")
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException, request: HttpServletRequest): ProblemDetail {
        logger.warn("Unauthorized: {} [{}]", ex.message, request.servletPath)
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, ex, request, "Unauthorized")
    }

    @ExceptionHandler(BadRequestException::class, IllegalArgumentException::class)
    fun handleBadRequest(ex: Exception, request: HttpServletRequest): ProblemDetail {
        logger.warn("BadRequest: {} [{}]", ex.message, request.servletPath)
        return buildProblemDetail(HttpStatus.BAD_REQUEST, ex, request, "Bad Request")
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDenied(
        ex: org.springframework.security.access.AccessDeniedException,
        request: HttpServletRequest
    ): ProblemDetail {
        logger.warn("AccessDenied: {} [{}]", ex.message, request.servletPath)
        return buildProblemDetail(HttpStatus.FORBIDDEN, ex, request, "Access Denied")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val validationErrors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        logger.warn("Validation Error: [{}]", request.servletPath)
        
        val problemDetail = buildProblemDetail(HttpStatus.BAD_REQUEST, ex, request, "Validation Error")
        problemDetail.setProperty("errors", validationErrors)
        return problemDetail
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ProblemDetail {
        logger.error("Internal Server Error at [{}]: {}", request.servletPath, ex.message, ex)
        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex, request, "Internal Server Error")
    }

    private fun buildProblemDetail(
        status: HttpStatus,
        ex: Exception,
        request: HttpServletRequest,
        title: String
    ): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(status, ex.message ?: status.reasonPhrase)
        problemDetail.title = title
        problemDetail.instance = URI.create(request.servletPath)
        problemDetail.setProperty("timestamp", Instant.now())
        return problemDetail
    }
}
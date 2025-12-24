package cl.clinipets.core.web

import java.time.Instant

data class ApiError(
    val message: String,
    val code: String,
    val status: Int,
    val timestamp: Instant = Instant.now(),
    val path: String? = null,
    val validationErrors: List<String>? = null
)

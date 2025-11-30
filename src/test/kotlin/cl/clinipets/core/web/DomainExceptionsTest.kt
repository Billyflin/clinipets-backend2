package cl.clinipets.core.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DomainExceptionsTest {

    @Test
    fun `DomainException should store the message correctly`() {
        val message = "This is a domain exception"
        val exception = DomainException(message)
        assertEquals(message, exception.message)
    }

    @Test
    fun `NotFoundException should store the message correctly`() {
        val message = "Resource not found"
        val exception = NotFoundException(message)
        assertEquals(message, exception.message)
    }

    @Test
    fun `ConflictException should store the message correctly`() {
        val message = "Conflict occurred"
        val exception = ConflictException(message)
        assertEquals(message, exception.message)
    }

    @Test
    fun `UnauthorizedException should store the message correctly`() {
        val message = "Unauthorized access"
        val exception = UnauthorizedException(message)
        assertEquals(message, exception.message)
    }

    @Test
    fun `BadRequestException should store the message correctly`() {
        val message = "Bad request received"
        val exception = BadRequestException(message)
        assertEquals(message, exception.message)
    }
}

package cl.clinipets.core.web

open class DomainException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : DomainException(message)
class UnauthorizedException(message: String) : DomainException(message)
class BadRequestException(message: String) : DomainException(message)

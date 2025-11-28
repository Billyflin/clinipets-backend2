package cl.clinipets.backend.servicios.dominio.excepciones

class PrecioNoDefinidoException(message: String = "No se encontró una regla de precio para el peso proporcionado") :
    RuntimeException(message)

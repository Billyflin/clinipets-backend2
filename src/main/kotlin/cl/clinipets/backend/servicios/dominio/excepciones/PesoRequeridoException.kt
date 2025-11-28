package cl.clinipets.backend.servicios.dominio.excepciones

class PesoRequeridoException(message: String = "El servicio requiere un peso de mascota para calcular el precio") :
    RuntimeException(message)

package cl.clinipets.core.config

import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.CategoriaServicio
import cl.clinipets.servicios.domain.ReglaPrecio
import cl.clinipets.servicios.domain.ServicioMedico
import cl.clinipets.servicios.domain.ServicioMedicoRepository
import cl.clinipets.veterinaria.domain.Especie
import cl.clinipets.veterinaria.domain.Mascota
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.Sexo
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Component
@Profile("!test")
class DataSeeder(
    private val servicioMedicoRepository: ServicioMedicoRepository,
    private val userRepository: UserRepository,
    private val mascotaRepository: MascotaRepository
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments?) {
        seedServices()
        seedUsersAndPets()
    }

    private fun seedServices() {
        // Definimos los servicios requeridos con sus duraciones
        crearOActualizarServicio(
            nombre = "Consulta General",
            duracion = 30,
            categoria = CategoriaServicio.CONSULTA,
            precioBase = 15000,
            precioAbono = 3000
        )

        crearOActualizarServicio(
            nombre = "Vacuna Octuple",
            duracion = 15,
            categoria = CategoriaServicio.VACUNA,
            precioBase = 12000,
            precioAbono = 2000,
            stock = 100
        )

        crearOActualizarServicio(
            nombre = "Esterilización Canina",
            duracion = 60,
            categoria = CategoriaServicio.CIRUGIA,
            precioBase = 45000,
            precioAbono = 5000,
            requierePeso = true,
            especies = mutableSetOf(Especie.PERRO)
        )

        crearOActualizarServicio(
            nombre = "Peluquería",
            duracion = 90,
            categoria = CategoriaServicio.OTRO,
            precioBase = 25000,
            precioAbono = 5000
        )

        // Servicio Dummy para pruebas de pago mínimas
        crearOActualizarServicio(
            nombre = "Test Flujo Pagos",
            duracion = 15,
            categoria = CategoriaServicio.OTRO,
            precioBase = 10,
            precioAbono = 5
        )
    }

    private fun crearOActualizarServicio(
        nombre: String,
        duracion: Int,
        categoria: CategoriaServicio,
        precioBase: Int,
        precioAbono: Int,
        requierePeso: Boolean = false,
        especies: MutableSet<Especie> = mutableSetOf(),
        stock: Int? = null
    ) {
        val existente = servicioMedicoRepository.findAll().find { it.nombre.equals(nombre, ignoreCase = true) }

        if (existente != null) {
            // Actualizar si es necesario
            existente.duracionMinutos = duracion
            existente.categoria = categoria
            servicioMedicoRepository.save(existente)
        } else {
            val servicio = ServicioMedico(
                nombre = nombre,
                precioBase = precioBase,
                precioAbono = precioAbono,
                requierePeso = requierePeso,
                duracionMinutos = duracion,
                activo = true,
                categoria = categoria,
                especiesPermitidas = especies,
                stock = stock
            )
            // Reglas básicas para servicios con peso (ejemplo simplificado)
            if (requierePeso) {
                servicio.reglas.add(
                    ReglaPrecio(
                        pesoMin = BigDecimal.ZERO,
                        pesoMax = BigDecimal("100.0"),
                        precio = precioBase,
                        servicio = servicio
                    )
                )
            }
            servicioMedicoRepository.save(servicio)
        }
    }

    private fun seedUsersAndPets() {
        val phone = "56945272297"
        val email = "billymartinezc@gmail.com"

        var user = userRepository.findByPhone(phone)

        if (user == null) {
            user = User(
                name = "Billy Developer",
                email = email,
                phone = phone,
                passwordHash = "dev_password_hash",
                role = UserRole.ADMIN
            )
            user = userRepository.save(user)
        }

        // Asegurar mascota para pruebas de reserva
        val mascotas = mascotaRepository.findAllByTutorId(user!!.id!!)
        if (mascotas.isEmpty()) {
            val firulais = Mascota(
                nombre = "Firulais Test",
                especie = Especie.PERRO,
                raza = "Mestizo",
                sexo = Sexo.MACHO,
                pesoActual = BigDecimal("10.0"),
                fechaNacimiento = LocalDate.now().minusYears(2),
                tutor = user,
                chipIdentificador = "TEST-CHIP-001"
            )
            mascotaRepository.save(firulais)
        }
    }
}

package cl.clinipets.core.config

import cl.clinipets.identity.domain.User
import cl.clinipets.identity.domain.UserRepository
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.servicios.domain.*
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
    private val mascotaRepository: MascotaRepository,
    private val promocionRepository: PromocionRepository
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments?) {
        seedServices()
        seedPromociones()
        seedUsersAndPets()
        configurarReglasYDependencias()
    }

    private fun seedServices() {
        // --- CONSULTAS ---
        crearOActualizarServicio(
            nombre = "Consulta Médica Primaria",
            duracion = 30,
            categoria = CategoriaServicio.CONSULTA,
            precioBase = 8000,
            precioAbono = 4000
        )
        crearOActualizarServicio(
            nombre = "Corte de uñas",
            duracion = 15,
            categoria = CategoriaServicio.CONSULTA,
            precioBase = 5000,
            precioAbono = 2500
        )

        // --- CHIP ---
        crearOActualizarServicio(
            nombre = "Implantación Microchip",
            duracion = 15,
            categoria = CategoriaServicio.OTRO,
            precioBase = 15000,
            precioAbono = 5000,
            especies = mutableSetOf(Especie.PERRO, Especie.GATO)
        )

        // --- EXAMENES ---
        crearOActualizarServicio(
            nombre = "Test Rápido Leucemia/VIF (Retroviral)",
            duracion = 15,
            categoria = CategoriaServicio.OTRO, // Mapeado a OTRO temporalmente si no existe EXAMEN en enum
            precioBase = 25000,
            precioAbono = 10000,
            especies = mutableSetOf(Especie.GATO)
        )

        // --- VACUNAS ---
        crearOActualizarServicio(
            nombre = "Vacuna Triple Felina",
            duracion = 15,
            categoria = CategoriaServicio.VACUNA,
            precioBase = 14000,
            precioAbono = 4000,
            especies = mutableSetOf(Especie.GATO)
        )
        crearOActualizarServicio(
            nombre = "Vacuna Leucemia Felina",
            duracion = 15,
            categoria = CategoriaServicio.VACUNA,
            precioBase = 14000,
            precioAbono = 4000,
            especies = mutableSetOf(Especie.GATO)
        )
        crearOActualizarServicio(
            nombre = "Vacuna Séxtuple",
            duracion = 15,
            categoria = CategoriaServicio.VACUNA,
            precioBase = 12000,
            precioAbono = 4000,
            especies = mutableSetOf(Especie.PERRO)
        )
        crearOActualizarServicio(
            nombre = "Vacuna KC (Tos de las perreras)",
            duracion = 15,
            categoria = CategoriaServicio.VACUNA,
            precioBase = 14000,
            precioAbono = 4000,
            especies = mutableSetOf(Especie.PERRO)
        )
        crearOActualizarServicio(
            nombre = "Vacuna Antirrábica",
            duracion = 15,
            categoria = CategoriaServicio.VACUNA,
            precioBase = 12000,
            precioAbono = 4000,
            especies = mutableSetOf(Especie.PERRO, Especie.GATO)
        )

        // --- CIRUGIAS ---
        crearOActualizarServicio(
            nombre = "Esterilización Felina Macho",
            duracion = 60,
            categoria = CategoriaServicio.CIRUGIA,
            precioBase = 25000,
            precioAbono = 10000,
            especies = mutableSetOf(Especie.GATO)
        )
        crearOActualizarServicio(
            nombre = "Esterilización Felina Hembra",
            duracion = 90,
            categoria = CategoriaServicio.CIRUGIA,
            precioBase = 30000,
            precioAbono = 10000,
            especies = mutableSetOf(Especie.GATO)
        )

        // --- CIRUGIA COMPLEJA CON REGLAS DE PESO ---
        seedEsterilizacionCanina()
    }

    private fun configurarReglasYDependencias() {
        val servicios = servicioMedicoRepository.findAll()
        val mapServicios = servicios.associateBy { it.nombre.lowercase() }

        // 1. Configurar dependencia de Vacuna Leucemia -> Test Retroviral
        val vacunaLeucemia = mapServicios["vacuna leucemia felina"]
        val testRetroviral = mapServicios["test rápido leucemia/vif (retroviral)"]

        if (vacunaLeucemia != null && testRetroviral != null) {
            if (!vacunaLeucemia.serviciosRequeridosIds.contains(testRetroviral.id!!)) {
                vacunaLeucemia.serviciosRequeridosIds.add(testRetroviral.id!!)
                servicioMedicoRepository.save(vacunaLeucemia)
            }
        }

        // 2. Configurar bloqueadoSiEsterilizado
        val serviciosEsterilizacion = listOf(
            "esterilización canina",
            "esterilización felina macho",
            "esterilización felina hembra"
        )

        serviciosEsterilizacion.forEach { nombre ->
            mapServicios[nombre]?.let {
                if (!it.bloqueadoSiEsterilizado) {
                    it.bloqueadoSiEsterilizado = true
                    servicioMedicoRepository.save(it)
                }
            }
        }
    }

    private fun seedEsterilizacionCanina() {
        val nombre = "Esterilización Canina"
        val existente = servicioMedicoRepository.findAll().find { it.nombre.equals(nombre, ignoreCase = true) }

        if (existente == null) {
            val servicio = ServicioMedico(
                nombre = nombre,
                precioBase = 30000, // Precio base (0-10kg)
                precioAbono = 10000,
                requierePeso = true,
                duracionMinutos = 90,
                activo = true,
                categoria = CategoriaServicio.CIRUGIA,
                especiesPermitidas = mutableSetOf(Especie.PERRO)
            )

            servicio.reglas.addAll(
                listOf(
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 0.0,
                        pesoMax = 10.0,
                        precio = 30000
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 10.1,
                        pesoMax = 15.0,
                        precio = 34000
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 15.1,
                        pesoMax = 20.0,
                        precio = 38000
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 20.1,
                        pesoMax = 25.0,
                        precio = 42000
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 25.1,
                        pesoMax = 30.0,
                        precio = 46000
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 30.1,
                        pesoMax = 35.0,
                        precio = 50000
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 35.1,
                        pesoMax = 40.0,
                        precio = 54000
                    )
                )
            )
            servicioMedicoRepository.save(servicio)
        }
    }

    private fun seedPromociones() {
        val nombrePromo = "Pack Cirugía + Microchip"

        // Verificar si ya existe para no duplicar
        val promos = promocionRepository.findAll()
        if (promos.any { it.nombre.equals(nombrePromo, ignoreCase = true) }) return

        // Buscar servicios
        val servicios = servicioMedicoRepository.findAll()
        val cirugia = servicios.find { it.nombre.contains("Esterilización Canina", ignoreCase = true) }
        val chip = servicios.find { it.nombre.contains("Microchip", ignoreCase = true) }

        if (cirugia != null && chip != null) {
            val promocion = Promocion(
                nombre = nombrePromo,
                descripcion = "Descuento al agendar esterilización canina junto con implantación de microchip fines de semana.",
                fechaInicio = LocalDate.now(),
                fechaFin = LocalDate.now().plusYears(1),
                diasPermitidos = "SAT,SUN",
                activa = true,
                serviciosTriggerIds = mutableSetOf(cirugia.id!!, chip.id!!)
            )

            promocion.beneficios.add(
                PromocionBeneficio(
                    servicioId = cirugia.id!!,
                    tipo = TipoDescuento.MONTO_OFF,
                    valor = BigDecimal(2000)
                )
            )
            promocion.beneficios.add(
                PromocionBeneficio(
                    servicioId = chip.id!!,
                    tipo = TipoDescuento.MONTO_OFF,
                    valor = BigDecimal(2000)
                )
            )

            promocionRepository.save(promocion)
        }
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
            existente.duracionMinutos = duracion
            existente.categoria = categoria
            existente.precioBase = precioBase
            existente.precioAbono = precioAbono
            if (especies.isNotEmpty()) {
                existente.especiesPermitidas = especies
            }
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
            // Regla por defecto para servicios simples
            if (requierePeso) {
                servicio.reglas.add(
                    ReglaPrecio(
                        pesoMin = 0.0,
                        pesoMax = 100.0,
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

        val mascotas = mascotaRepository.findAllByTutorId(user!!.id!!)
        if (mascotas.isEmpty()) {
            val firulais = Mascota(
                nombre = "Firulais Test",
                especie = Especie.PERRO,
                raza = "Mestizo",
                sexo = Sexo.MACHO,
                pesoActual = 10.0,
                fechaNacimiento = LocalDate.now().minusYears(2),
                tutor = user,
                chipIdentificador = "TEST-CHIP-001"
            )
            mascotaRepository.save(firulais)
        }
    }
}
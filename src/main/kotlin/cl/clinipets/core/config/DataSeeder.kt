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
                precioBase = BigDecimal(30000), // Precio base (0-10kg)
                precioAbono = BigDecimal(10000),
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
                        precio = BigDecimal(30000)
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 10.1,
                        pesoMax = 15.0,
                        precio = BigDecimal(34000)
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 15.1,
                        pesoMax = 20.0,
                        precio = BigDecimal(38000)
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 20.1,
                        pesoMax = 25.0,
                        precio = BigDecimal(42000)
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 25.1,
                        pesoMax = 30.0,
                        precio = BigDecimal(46000)
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 30.1,
                        pesoMax = 35.0,
                        precio = BigDecimal(50000)
                    ),
                    ReglaPrecio(
                        servicio = servicio,
                        pesoMin = 35.1,
                        pesoMax = 40.0,
                        precio = BigDecimal(54000)
                    )
                )
            )
            servicioMedicoRepository.save(servicio)
        }
    }

    private fun seedPromociones() {
        if (promocionRepository.count() == 0L) {
            val consulta = servicioMedicoRepository.findAll().find { it.nombre.contains("Consulta") }!!
            val vacuna = servicioMedicoRepository.findAll().find { it.nombre.contains("Vacuna") }!!

            // Promo 1: Pack Cachorro (Consulta + Vacuna) -> $5.000 dcto en Consulta
            val promoCachorro = Promocion(
                nombre = "Pack Cachorro",
                descripcion = "Descuento al llevar Consulta y Vacuna juntas",
                fechaInicio = LocalDate.now(),
                fechaFin = LocalDate.now().plusYears(1),
                activa = true,
                serviciosTrigger = mutableSetOf(consulta, vacuna)
            )
            promoCachorro.beneficios.add(
                PromocionBeneficio(
                    servicio = consulta,
                    tipo = TipoDescuento.MONTO_OFF,
                    valor = BigDecimal(5000)
                )
            )
            promocionRepository.save(promoCachorro)

            // Promo 2: Fin de Semana Quirúrgico (Si incluye Cirugía y Chip -> Descuento en ambos)
            val cirugia = servicioMedicoRepository.findAll().find { it.nombre.contains("Esterilización") } ?: return
            val chip = servicioMedicoRepository.findAll().find { it.nombre.contains("Chip") } ?: return

            val promocion = Promocion(
                nombre = "Fin de Semana Quirúrgico",
                descripcion = "20% off en cirugías los fines de semana",
                fechaInicio = LocalDate.now(),
                fechaFin = LocalDate.now().plusYears(1),
                diasPermitidos = "SAT,SUN",
                activa = true,
                serviciosTrigger = mutableSetOf(cirugia, chip)
            )

            promocion.beneficios.add(
                PromocionBeneficio(
                    servicio = cirugia,
                    tipo = TipoDescuento.MONTO_OFF,
                    valor = BigDecimal(2000)
                )
            )
            promocion.beneficios.add(
                PromocionBeneficio(
                    servicio = chip,
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
            existente.precioBase = BigDecimal(precioBase)
            existente.precioAbono = BigDecimal(precioAbono)
            if (especies.isNotEmpty()) {
                existente.especiesPermitidas = especies
            }
            servicioMedicoRepository.save(existente)
        } else {
            val servicio = ServicioMedico(
                nombre = nombre,
                precioBase = BigDecimal(precioBase),
                precioAbono = BigDecimal(precioAbono),
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
                        precio = BigDecimal(precioBase),
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
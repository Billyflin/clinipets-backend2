package cl.clinipets.veterinaria.historial.application

import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.api.SignosVitalesDto
import cl.clinipets.veterinaria.api.toDto
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.PlanPreventivoRepository
import cl.clinipets.veterinaria.domain.SignosVitalesRepository
import cl.clinipets.veterinaria.domain.TipoPreventivo
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class HistorialCompletoResponse(
    val mascotaId: UUID,
    val nombreMascota: String,
    val fichasClinicas: List<ResumenFichaDto>,
    val planPreventivo: PlanPreventivoResumen,
    val signosVitales: EvolucionSignosVitales,
    val marcadoresActuales: Map<String, String>,
    val hitosRecientes: List<HitoMedicoDto>
)

data class HitoMedicoDto(
    val id: UUID,
    val marcador: String,
    val valorAnterior: String?,
    val valorNuevo: String,
    val fecha: Instant,
    val motivo: String?
)

data class ResumenFichaDto(
    val id: UUID,
    val fecha: Instant,
    val motivo: String,
    val diagnostico: String?,
    val veterinario: String
)

data class PlanPreventivoResumen(
    val vacunasCompletas: Boolean,
    val ultimaVacuna: VacunaDto?,
    val proximaVacuna: VacunaDto?,
    val ultimaDesparasitacion: DesparasitacionDto?,
    val alertas: List<AlertaPreventiva>
)

data class VacunaDto(
    val nombre: String,
    val fecha: Instant,
    val refuerzo: Instant?
)

data class DesparasitacionDto(
    val tipo: TipoPreventivo,
    val producto: String,
    val fecha: Instant
)

data class AlertaPreventiva(
    val tipo: String,
    val mensaje: String,
    val prioridad: PrioridadAlerta
)

enum class PrioridadAlerta {
    BAJA, MEDIA, ALTA, CRITICA
}

data class EvolucionSignosVitales(
    val ultimoRegistro: SignosVitalesDto?,
    val pesoPromedio: Double?,
    val tendenciaPeso: TendenciaPeso,
    val alertas: List<String>
)

enum class TendenciaPeso {
    ESTABLE, AUMENTO, DISMINUCION, FLUCTUANTE
}

data class HistorialEvolucionDto(
    val mascotaId: UUID,
    val nombreMascota: String,
    val registros: List<PuntoEvolucionDto>
)

data class PuntoEvolucionDto(
    val fecha: Instant,
    val peso: Double?,
    val temperatura: Double?,
    val origen: String // "FICHA" o "CONTROL"
)

@Service
class HistorialClinicoService(
    private val mascotaRepository: MascotaRepository,
    private val fichaClinicaRepository: FichaClinicaRepository,
    private val planPreventivoRepository: PlanPreventivoRepository,
    private val signosVitalesRepository: SignosVitalesRepository,
    private val hitoMedicoRepository: cl.clinipets.veterinaria.domain.HitoMedicoRepository
) {
    private val logger = LoggerFactory.getLogger(HistorialClinicoService::class.java)

    @Transactional(readOnly = true)
    fun obtenerEvolucionMedica(mascotaId: UUID, user: JwtPayload): HistorialEvolucionDto {
        val mascota = mascotaRepository.findById(mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada") }

        if (user.role == UserRole.CLIENT && mascota.tutor.id != user.userId) {
            throw UnauthorizedException("No tienes permiso para ver esta evolución")
        }

        // Obtener datos de Fichas
        val fichas = fichaClinicaRepository.findAllByMascotaIdOrderByFechaAtencionAsc(mascotaId)
        val puntosFicha = fichas.map {
            PuntoEvolucionDto(
                fecha = it.fechaAtencion,
                peso = it.signosVitales.pesoRegistrado,
                temperatura = it.signosVitales.temperatura,
                origen = "FICHA"
            )
        }

        // Obtener datos de controles de signos vitales (independientes de ficha)
        val vitales = signosVitalesRepository.findAllByMascotaIdOrderByFechaDesc(mascotaId)
        val puntosControl = vitales.map {
            PuntoEvolucionDto(
                fecha = it.fecha,
                peso = it.peso,
                temperatura = it.temperatura,
                origen = "CONTROL"
            )
        }

        val todosLosPuntos = (puntosFicha + puntosControl)
            .filter { it.peso != null || it.temperatura != null }
            .sortedBy { it.fecha }

        return HistorialEvolucionDto(
            mascotaId = mascota.id!!,
            nombreMascota = mascota.nombre,
            registros = todosLosPuntos
        )
    }

    @Transactional(readOnly = true)
    fun obtenerHistorialCompleto(mascotaId: UUID, user: JwtPayload): HistorialCompletoResponse {
        logger.info("[HISTORIAL_COMPLETO] Consultando para mascota: $mascotaId")
        
        val mascota = mascotaRepository.findById(mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada") }

        // Validar permisos
        if (user.role == UserRole.CLIENT && mascota.tutor.id != user.userId) {
            throw UnauthorizedException("No tienes permiso para ver este historial")
        }

        // 1. Fichas clínicas
        val fichas = fichaClinicaRepository.findAllByMascotaIdOrderByFechaAtencionAsc(mascotaId)
        val fichasResumen = fichas.map { 
            ResumenFichaDto(
                id = it.id!!,
                fecha = it.fechaAtencion,
                motivo = it.motivoConsulta,
                diagnostico = it.avaluoClinico,
                veterinario = "Veterinario Tratante" // TODO: Vincular con User
            )
        }

        // 2. Plan preventivo
        val preventivos = planPreventivoRepository.findAllByMascotaIdOrderByFechaAplicacionDesc(mascotaId)
        val planResumen = generarResumenPreventivo(preventivos, mascota.especie)

        // 3. Signos vitales
        val vitales = signosVitalesRepository.findAllByMascotaIdOrderByFechaDesc(mascotaId)
        val evolucion = analizarEvolucionSignosVitales(vitales)

        // 4. Marcadores e Hitos
        val hitos = hitoMedicoRepository.findAllByMascotaIdOrderByFechaDesc(mascotaId)
        val hitosDto = hitos.map {
            HitoMedicoDto(
                id = it.id!!,
                marcador = it.marcador,
                valorAnterior = it.valorAnterior,
                valorNuevo = it.valorNuevo,
                fecha = it.fecha,
                motivo = it.motivo
            )
        }

        return HistorialCompletoResponse(
            mascotaId = mascota.id!!,
            nombreMascota = mascota.nombre,
            fichasClinicas = fichasResumen,
            planPreventivo = planResumen,
            signosVitales = evolucion,
            marcadoresActuales = mascota.marcadores,
            hitosRecientes = hitosDto
        )
    }

    private fun generarResumenPreventivo(
        preventivos: List<cl.clinipets.veterinaria.domain.PlanPreventivo>,
        especie: cl.clinipets.veterinaria.domain.Especie
    ): PlanPreventivoResumen {
        val vacunas = preventivos.filter { it.tipo == TipoPreventivo.VACUNA }
        val desparasitaciones = preventivos.filter { 
            it.tipo == TipoPreventivo.DESPARASITACION_INTERNA || 
            it.tipo == TipoPreventivo.DESPARASITACION_EXTERNA 
        }

        val ahora = Instant.now()
        val proximasVacunas = vacunas
            .filter { it.fechaRefuerzo != null && it.fechaRefuerzo!! > ahora }
            .sortedBy { it.fechaRefuerzo }

        val alertas = mutableListOf<AlertaPreventiva>()

        // Validar vacunas pendientes críticas
        proximasVacunas.firstOrNull()?.let { proxima ->
            val diasRestantes = ChronoUnit.DAYS.between(ahora, proxima.fechaRefuerzo)
            when {
                diasRestantes <= 0 -> alertas.add(
                    AlertaPreventiva(
                        "VACUNA_VENCIDA",
                        "Refuerzo de ${proxima.producto} está vencido",
                        PrioridadAlerta.CRITICA
                    )
                )
                diasRestantes <= 7 -> alertas.add(
                    AlertaPreventiva(
                        "VACUNA_URGENTE",
                        "Refuerzo de ${proxima.producto} en $diasRestantes días",
                        PrioridadAlerta.ALTA
                    )
                )
                diasRestantes <= 30 -> alertas.add(
                    AlertaPreventiva(
                        "VACUNA_PROXIMA",
                        "Refuerzo de ${proxima.producto} en $diasRestantes días",
                        PrioridadAlerta.MEDIA
                    )
                )
                else -> {}
            }
        }

        // Validar desparasitación (recomendación cada 3 meses)
        val ultimaDesp = desparasitaciones.firstOrNull()
        if (ultimaDesp != null) {
            val mesesDesdeUltima = ChronoUnit.DAYS.between(ultimaDesp.fechaAplicacion, ahora) / 30
            if (mesesDesdeUltima >= 3) {
                alertas.add(
                    AlertaPreventiva(
                        "DESPARASITACION_PENDIENTE",
                        "Han pasado $mesesDesdeUltima meses desde la última desparasitación",
                        PrioridadAlerta.MEDIA
                    )
                )
            }
        } else {
            alertas.add(
                AlertaPreventiva(
                    "SIN_DESPARASITACION",
                    "No hay registro de desparasitaciones",
                    PrioridadAlerta.ALTA
                )
            )
        }

        return PlanPreventivoResumen(
            vacunasCompletas = validarEsquemaCompleto(vacunas, especie),
            ultimaVacuna = vacunas.firstOrNull()?.let {
                VacunaDto(it.producto, it.fechaAplicacion, it.fechaRefuerzo)
            },
            proximaVacuna = proximasVacunas.firstOrNull()?.let {
                VacunaDto(it.producto, it.fechaRefuerzo!!, null)
            },
            ultimaDesparasitacion = ultimaDesp?.let {
                DesparasitacionDto(it.tipo, it.producto, it.fechaAplicacion)
            },
            alertas = alertas
        )
    }

    private fun validarEsquemaCompleto(
        vacunas: List<cl.clinipets.veterinaria.domain.PlanPreventivo>,
        especie: cl.clinipets.veterinaria.domain.Especie
    ): Boolean {
        val vacunasRequeridas = if (especie == cl.clinipets.veterinaria.domain.Especie.PERRO) {
            setOf("Séxtuple", "Antirrábica")
        } else if (especie == cl.clinipets.veterinaria.domain.Especie.GATO) {
            setOf("Triple Felina", "Antirrábica")
        } else {
            emptySet()
        }

        val vacunasAplicadas = vacunas.map { 
            it.producto.lowercase() 
        }.toSet()

        return vacunasRequeridas.all { requerida ->
            vacunasAplicadas.any { aplicada -> aplicada.contains(requerida.lowercase()) }
        }
    }

    private fun analizarEvolucionSignosVitales(
        vitales: List<cl.clinipets.veterinaria.domain.SignosVitales>
    ): EvolucionSignosVitales {
        if (vitales.isEmpty()) {
            return EvolucionSignosVitales(
                ultimoRegistro = null,
                pesoPromedio = null,
                tendenciaPeso = TendenciaPeso.ESTABLE,
                alertas = listOf("No hay registros de signos vitales")
            )
        }

        val ultimo = vitales.first().toDto()
        val pesos = vitales.mapNotNull { it.peso }
        val pesoPromedio = if (pesos.isNotEmpty()) pesos.average() else null

        // Calcular tendencia (últimos 3 registros vs anteriores)
        val tendencia = if (pesos.size >= 4) {
            val recientes = pesos.take(3).average()
            val anteriores = pesos.drop(3).take(3).average()
            when {
                recientes > anteriores * 1.1 -> TendenciaPeso.AUMENTO
                recientes < anteriores * 0.9 -> TendenciaPeso.DISMINUCION
                else -> TendenciaPeso.ESTABLE
            }
        } else {
            TendenciaPeso.ESTABLE
        }

        val alertas = mutableListOf<String>()

        // Validar temperatura del último registro
        if (ultimo.temperatura > 39.5) {
            alertas.add("⚠️ Última temperatura elevada: ${ultimo.temperatura}°C")
        }

        // Alerta de variación de peso
        when (tendencia) {
            TendenciaPeso.AUMENTO -> alertas.add("📈 Tendencia de aumento de peso detectada")
            TendenciaPeso.DISMINUCION -> alertas.add("📉 Tendencia de pérdida de peso detectada")
            TendenciaPeso.FLUCTUANTE -> alertas.add("⚠️ Peso fluctuante - requiere seguimiento")
            else -> {}
        }

        return EvolucionSignosVitales(
            ultimoRegistro = ultimo,
            pesoPromedio = pesoPromedio,
            tendenciaPeso = tendencia,
            alertas = alertas
        )
    }
}
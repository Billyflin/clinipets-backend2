package cl.clinipets.veterinaria.historial.application

import cl.clinipets.agendamiento.domain.Cita
import cl.clinipets.agendamiento.domain.CitaRepository
import cl.clinipets.agendamiento.domain.EstadoCita
import cl.clinipets.core.config.ClinicProperties
import cl.clinipets.core.security.JwtPayload
import cl.clinipets.core.storage.StorageService
import cl.clinipets.core.web.NotFoundException
import cl.clinipets.core.web.UnauthorizedException
import cl.clinipets.identity.domain.UserRole
import cl.clinipets.veterinaria.galeria.domain.MascotaMediaRepository
import cl.clinipets.veterinaria.galeria.domain.MediaType
import cl.clinipets.veterinaria.historial.domain.FichaClinicaRepository
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max

@Service
class PdfService(
    private val fichaClinicaRepository: FichaClinicaRepository,
    private val citaRepository: CitaRepository,
    private val mascotaMediaRepository: MascotaMediaRepository,
    private val storageService: StorageService,
    private val clinicProperties: ClinicProperties,
    private val clinicZoneId: ZoneId
) {
    private val logger = LoggerFactory.getLogger(PdfService::class.java)

    // Fuentes configuradas (Ver FontFactoryHelper abajo para el fix)
    private val baseFont: Font = FontFactoryHelper.base(10f)
    private val smallFont: Font = FontFactoryHelper.base(9f)
    private val boldFont: Font = FontFactoryHelper.bold(11f)
    private val titleFont: Font = FontFactoryHelper.bold(16f)
    private val sectionFont: Font = FontFactoryHelper.bold(12f)
    private val noteFont: Font = FontFactoryHelper.base(8f)
    private val headerFont: Font = FontFactoryHelper.bold(13f, Color(0, 122, 163))

    data class FinancialSummary(
        val servicioNombre: String,
        val servicioPrecio: Int,
        val insumosExtras: Int,
        val totalPagado: Int,
        val estadoCita: EstadoCita?
    )

    @Transactional(readOnly = true)
    fun generarFichaClinicaPdf(fichaId: UUID, requester: JwtPayload): ByteArray {
        val ficha = fichaClinicaRepository.findById(fichaId)
            .orElseThrow { NotFoundException("Ficha clínica no encontrada") }

        val mascota = ficha.mascota
        val tutor = mascota.tutor

        // Validación de seguridad: solo el dueño o staff puede verla
        if (requester.role == UserRole.CLIENT && tutor.id != requester.userId) {
            logger.warn("[PDF] Acceso denegado. User {} no es dueño de la mascota {}", requester.email, mascota.id)
            throw UnauthorizedException("No tiene permiso para ver esta ficha.")
        }

        // Buscar datos financieros relacionados
        val citaRelacionada = mascota.id?.let { buscarCitaRelacionada(it, ficha.fechaAtencion) }
        val resumenFinanciero = buildFinancialSummary(citaRelacionada, ficha.motivoConsulta)

        // Generación del PDF
        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 36f, 36f, 36f, 48f)
        PdfWriter.getInstance(document, baos)
        document.open()

        addHeader(document)
        addTitle(document)
        addPacienteInfo(
            document,
            ficha.fechaAtencion,
            mascota.nombre,
            mascota.especie.name,
            tutor.name,
            tutor.phone,
            tutor.email,
            tutor.address,
            ficha.pesoRegistrado ?: mascota.pesoActual.toDouble()
        )

        // Secciones Médicas
        addClinicalSection(document, "Motivo", ficha.motivoConsulta)
        addClinicalSection(document, "Anamnesis", ficha.anamnesis)
        addClinicalSection(document, "Examen Físico", ficha.examenFisico)
        addClinicalSection(document, "Diagnóstico", ficha.diagnostico)
        addClinicalSection(document, "Tratamiento", ficha.tratamiento)
        ficha.observaciones?.let { addClinicalSection(document, "Observaciones", it) }

        // Secciones Finales
        addFinancialSection(document, resumenFinanciero)
        addFootNote(document)
        addPhotoIfExists(document, mascota.id, mascota.nombre)

        document.close()
        return baos.toByteArray()
    }

    private fun addHeader(document: Document) {
        val table = PdfPTable(floatArrayOf(1.1f, 2f))
        table.widthPercentage = 100f

        val logoCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            setPadding(4f)
        }
        logoCell.addElement(
            Paragraph(clinicProperties.name, headerFont).apply { alignment = Element.ALIGN_LEFT }
        )
        clinicProperties.website.takeIf { it.isNotBlank() }?.let {
            logoCell.addElement(
                Paragraph(it, smallFont).apply {
                    alignment = Element.ALIGN_LEFT
                    spacingBefore = -2f
                }
            )
        }
        table.addCell(logoCell)

        val infoCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_RIGHT
            setPadding(4f)
        }
        infoCell.addElement(Paragraph(clinicProperties.name, boldFont).apply { alignment = Element.ALIGN_RIGHT })
        clinicProperties.rut.takeIf { it.isNotBlank() }?.let {
            infoCell.addElement(Paragraph("RUT: $it", baseFont).apply { alignment = Element.ALIGN_RIGHT })
        }
        clinicProperties.address.takeIf { it.isNotBlank() }?.let {
            infoCell.addElement(Paragraph(it, baseFont).apply { alignment = Element.ALIGN_RIGHT })
        }
        val contactLine = listOfNotNull(
            clinicProperties.phone.takeIf { it.isNotBlank() },
            clinicProperties.email.takeIf { it.isNotBlank() }
        ).joinToString(" | ")
        if (contactLine.isNotBlank()) {
            infoCell.addElement(Paragraph(contactLine, baseFont).apply { alignment = Element.ALIGN_RIGHT })
        }
        table.addCell(infoCell)

        document.add(table)
    }

    private fun addTitle(document: Document) {
        val title = Paragraph("FICHA CLÍNICA Y COMPROBANTE DE ATENCIÓN", titleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingBefore = 10f
            spacingAfter = 14f
        }
        document.add(title)
    }

    private fun addPacienteInfo(
        document: Document,
        fechaAtencion: java.time.Instant,
        nombreMascota: String,
        especie: String,
        tutorNombre: String,
        tutorTelefono: String?,
        tutorCorreo: String?,
        direccion: String?,
        peso: Double
    ) {
        val table = PdfPTable(floatArrayOf(1f, 1f))
        table.widthPercentage = 100f
        table.setSpacingAfter(12f)

        val fechaLocal = fechaAtencion.atZone(clinicZoneId)
        val fechaTexto = fechaLocal.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

        table.addCell(infoCell("Paciente", nombreMascota))
        table.addCell(infoCell("Fecha atención", fechaTexto))
        table.addCell(infoCell("Especie", especie.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }))
        table.addCell(infoCell("Peso registrado", "${"%.2f".format(peso)} kg"))
        table.addCell(infoCell("Tutor", tutorNombre))
        table.addCell(infoCell("Teléfono", tutorTelefono ?: "Sin registro"))
        table.addCell(infoCell("Correo", tutorCorreo ?: "Sin registro"))
        table.addCell(infoCell("Dirección", direccion ?: "Sin registro"))

        document.add(table)
    }

    private fun infoCell(label: String, value: String): PdfPCell {
        val phrase = Phrase().apply {
            add(Phrase("$label: ", boldFont))
            add(Phrase(value, baseFont))
        }
        return PdfPCell(phrase).apply {
            backgroundColor = Color(245, 249, 252)
            setPadding(8f)
            border = Rectangle.NO_BORDER
        }
    }

    private fun addClinicalSection(document: Document, title: String, content: String?) {
        val safeContent = content?.takeIf { it.isNotBlank() } ?: "Sin registro."
        val sectionTitle = Paragraph(title.uppercase(), sectionFont).apply {
            spacingBefore = 6f
            spacingAfter = 2f
        }
        val body = Paragraph(safeContent, baseFont).apply { spacingAfter = 6f }
        document.add(sectionTitle)
        document.add(body)
    }

    private fun addFinancialSection(document: Document, summary: FinancialSummary) {
        val heading = Paragraph("RESUMEN FINANCIERO", sectionFont).apply {
            spacingBefore = 10f
            spacingAfter = 6f
        }
        document.add(heading)

        val table = PdfPTable(floatArrayOf(3f, 1f)).apply {
            widthPercentage = 100f
            setSpacingAfter(8f)
        }

        fun addRow(label: String, amount: Int, highlight: Boolean = false) {
            val labelCell = PdfPCell(Phrase(label, if (highlight) boldFont else baseFont)).apply {
                border = Rectangle.NO_BORDER
                setPadding(6f)
                backgroundColor = Color(248, 251, 253)
            }
            val valueCell = PdfPCell(Phrase(formatCurrency(amount), if (highlight) boldFont else baseFont)).apply {
                border = Rectangle.NO_BORDER
                setPadding(6f)
                horizontalAlignment = Element.ALIGN_RIGHT
                backgroundColor = Color(248, 251, 253)
            }
            table.addCell(labelCell)
            table.addCell(valueCell)
        }

        addRow("Servicio: ${summary.servicioNombre}", summary.servicioPrecio)
        addRow("Insumos/Extras", summary.insumosExtras)
        addRow("TOTAL PAGADO", summary.totalPagado, highlight = true)

        summary.estadoCita?.let {
            val estadoLegible = it.name.lowercase().replace('_', ' ').replaceFirstChar { ch -> ch.titlecase() }
            val estadoCell = PdfPCell(Phrase("Estado de la cita: $estadoLegible", smallFont)).apply {
                border = Rectangle.NO_BORDER
                paddingTop = 4f
                paddingBottom = 0f
                colspan = 2
            }
            table.addCell(estadoCell)
        }

        document.add(table)
    }

    private fun addFootNote(document: Document) {
        val note = Paragraph(
            "Este documento es un resumen clínico y comprobante de pago interno. Si pagó con tarjeta, su voucher es válido como boleta.",
            noteFont
        ).apply {
            spacingBefore = 2f
        }
        document.add(note)
    }

    private fun addPhotoIfExists(document: Document, mascotaId: UUID?, nombreMascota: String) {
        if (mascotaId == null) return

        val foto = mascotaMediaRepository.findAllByMascotaIdOrderByFechaSubidaDesc(mascotaId)
            .firstOrNull { it.tipo == MediaType.IMAGE }
            ?: run {
                logger.info("[PDF] Mascota {} sin foto asociada para la ficha", mascotaId)
                return
            }

        val image = try {
            // Obtenemos los bytes desde MinIO/S3
            val imageBytes = storageService.getFile(foto.url).use { input -> input.readAllBytes() }
            Image.getInstance(imageBytes)
        } catch (ex: Exception) {
            logger.warn("[PDF] No se pudo cargar la foto {}: {}", foto.id ?: "sin-id", ex.message)
            return
        }

        // Ajuste de tamaño para que no rompa la página
        image.scaleToFit(450f, 360f)
        image.alignment = Element.ALIGN_CENTER

        val caption = Paragraph("Foto de $nombreMascota", smallFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingBefore = 6f
            spacingAfter = 4f
        }

        document.add(caption)
        document.add(image)
    }

    private fun buscarCitaRelacionada(mascotaId: UUID, fechaFicha: java.time.Instant): Cita? {
        val citas = citaRepository.findAllByMascotaId(mascotaId)
        val fechaLocal = fechaFicha.atZone(clinicZoneId).toLocalDate()
        // Buscamos una cita que coincida exactamente con la fecha, o la más reciente
        return citas.firstOrNull { it.fechaHoraInicio.atZone(clinicZoneId).toLocalDate() == fechaLocal }
            ?: citas.firstOrNull()
    }

    private fun buildFinancialSummary(cita: Cita?, fallbackLabel: String): FinancialSummary {
        if (cita == null) {
            return FinancialSummary(
                servicioNombre = fallbackLabel,
                servicioPrecio = 0,
                insumosExtras = 0,
                totalPagado = 0,
                estadoCita = null
            )
        }

        val serviciosLabel = if (cita.detalles.isEmpty()) {
            "Atención clínica"
        } else {
            cita.detalles.joinToString(", ") { it.servicio.nombre }
        }

        val subtotalServicios = if (cita.detalles.isEmpty()) {
            cita.precioFinal
        } else {
            cita.detalles.sumOf { it.precioUnitario }
        }

        val extras = max(cita.precioFinal - subtotalServicios, 0)
        val saldoPendiente = when (cita.estado) {
            EstadoCita.PENDIENTE_PAGO -> cita.precioFinal
            EstadoCita.FINALIZADA, EstadoCita.CANCELADA -> 0 // Si finalizó, asumimos pagado
            else -> max(cita.precioFinal - cita.montoAbono, 0)
        }
        val pagado = max(cita.precioFinal - saldoPendiente, 0)

        return FinancialSummary(
            servicioNombre = serviciosLabel,
            servicioPrecio = subtotalServicios,
            insumosExtras = extras,
            totalPagado = pagado,
            estadoCita = cita.estado
        )
    }

    private fun formatCurrency(amount: Int): String {
        val formatter = java.text.NumberFormat.getInstance(Locale.of("es", "CL")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
        return "$${formatter.format(amount)}"
    }
}

// -----------------------------------------------------------
// HELPER PARA FUENTES (CORREGIDO)
// -----------------------------------------------------------
private object FontFactoryHelper {
    // FIX: Usar BaseFont.WINANSI (CP1252) en lugar de IDENTITY_H para fuentes estándar.
    // Esto soporta tildes y eñes sin requerir un archivo .ttf externo.

    fun base(size: Float, color: Color = Color(40, 40, 40)): Font =
        com.lowagie.text.FontFactory.getFont(
            com.lowagie.text.FontFactory.HELVETICA,
            BaseFont.WINANSI, // <--- CAMBIO CLAVE AQUÍ
            BaseFont.EMBEDDED,
            size,
            Font.NORMAL,
            color
        )

    fun bold(size: Float, color: Color = Color(30, 30, 30)): Font =
        com.lowagie.text.FontFactory.getFont(
            com.lowagie.text.FontFactory.HELVETICA_BOLD,
            BaseFont.WINANSI, // <--- CAMBIO CLAVE AQUÍ
            BaseFont.EMBEDDED,
            size,
            Font.BOLD,
            color
        )
}

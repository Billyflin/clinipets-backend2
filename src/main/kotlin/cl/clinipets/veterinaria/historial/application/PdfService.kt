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
import cl.clinipets.veterinaria.domain.MascotaRepository
import cl.clinipets.veterinaria.domain.PlanPreventivoRepository
import cl.clinipets.veterinaria.domain.TipoPreventivo
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
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

@Service
class PdfService(
    private val fichaClinicaRepository: FichaClinicaRepository,
    private val citaRepository: CitaRepository,
    private val mascotaMediaRepository: MascotaMediaRepository,
    private val storageService: StorageService,
    private val clinicProperties: ClinicProperties,
    private val clinicZoneId: ZoneId,
    private val userRepository: cl.clinipets.identity.domain.UserRepository,
    private val mascotaRepository: MascotaRepository,
    private val planPreventivoRepository: PlanPreventivoRepository
) {
    private val logger = LoggerFactory.getLogger(PdfService::class.java)

    // Fuentes configuradas
    private val baseFont: Font = FontFactoryHelper.base(10f)
    private val smallFont: Font = FontFactoryHelper.base(9f)
    private val boldFont: Font = FontFactoryHelper.bold(11f)
    private val titleFont: Font = FontFactoryHelper.bold(16f)
    private val sectionFont: Font = FontFactoryHelper.bold(12f)
    private val noteFont: Font = FontFactoryHelper.base(8f)
    private val headerFont: Font = FontFactoryHelper.bold(13f, Color(0, 122, 163))
    private val vaccineFont: Font = FontFactoryHelper.bold(14f, Color(0, 100, 0))
    private val alertFont: Font = FontFactoryHelper.bold(10f, Color.RED)

    data class FinancialSummary(
        val servicioNombre: String,
        val servicioPrecio: BigDecimal,
        val insumosExtras: BigDecimal,
        val totalPagado: BigDecimal,
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

        val authorName = ficha.autor.name

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
            ficha.signosVitales.pesoRegistrado ?: mascota.pesoActual.toDouble()
        )

        // Signos Vitales Estructurados
        addVitalSignsSection(document, ficha)

        // Secciones Médicas (SOAP)
        addClinicalSection(document, "Motivo", ficha.motivoConsulta)
        addClinicalSection(document, "Anamnesis (Subjetivo)", ficha.anamnesis)
        addClinicalSection(document, "Hallazgos Objetivos (Examen Físico)", ficha.hallazgosObjetivos)
        addClinicalSection(document, "Avalúo Clínico (Diagnóstico)", ficha.avaluoClinico)
        addClinicalSection(document, "Plan de Tratamiento e Indicaciones", ficha.planTratamiento)
        ficha.observaciones?.let { addClinicalSection(document, "Observaciones adicionales", it) }

        // Plan de Vacunación Destacado
        if (ficha.planSanitario.esVacuna) {
            addVaccineSection(document, ficha.planSanitario.nombreVacuna, ficha.planSanitario.fechaProximaVacuna)
        }

        // Secciones Finales
        addFinancialSection(document, resumenFinanciero)
        addPhotoIfExists(document, mascota.id, mascota.nombre)
        addSignature(document, authorName)
        addFootNote(document)

        document.close()
        return baos.toByteArray()
    }

    @Transactional(readOnly = true)
    fun generarCarnetSanitarioPdf(mascotaId: UUID, user: JwtPayload): ByteArray {
        logger.info("[CARNET_PDF] Generando PDF para mascota $mascotaId")

        val mascota = mascotaRepository.findById(mascotaId)
            .orElseThrow { NotFoundException("Mascota no encontrada: $mascotaId") }

        if (user.role == UserRole.CLIENT && mascota.tutor.id != user.userId) {
            throw UnauthorizedException("No tienes permiso para ver este carnet")
        }

        val preventivos = planPreventivoRepository.findAllByMascotaIdOrderByFechaAplicacionDesc(mascotaId)
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 40f, 40f, 40f, 40f)
        PdfWriter.getInstance(document, outputStream)

        document.open()

        // Logo
        try {
            val logoPath = clinicProperties.logoPath
            if (!logoPath.isNullOrBlank()) {
                val logoStream = storageService.getFile(logoPath)
                val logoBytes = logoStream.readAllBytes()
                val logo = Image.getInstance(logoBytes)
                logo.scaleToFit(150f, 60f)
                logo.alignment = Element.ALIGN_CENTER
                document.add(logo)
            }
        } catch (ex: Exception) {
            logger.warn("[CARNET_PDF] No se pudo cargar el logo: ${ex.message}")
        }

        // Título
        val titulo = Paragraph("CARNET SANITARIO", titleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 20f
        }
        document.add(titulo)

        // Info Clínica
        val clinicInfo = Paragraph().apply {
            add(Phrase("${clinicProperties.name}\n", boldFont))
            add(Phrase("${clinicProperties.address}\n", baseFont))
            add(Phrase("Tel: ${clinicProperties.phone}\n", baseFont))
            alignment = Element.ALIGN_CENTER
            spacingAfter = 20f
        }
        document.add(clinicInfo)

        // Datos Mascota
        val tablaMascota = PdfPTable(2).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(1f, 2f))
            setSpacingAfter(20f)
        }

        fun agregarFila(label: String, valor: String) {
            val l = PdfPCell(Phrase(label, boldFont)).apply {
                border = Rectangle.NO_BORDER
                setPadding(5f)
            }
            val v = PdfPCell(Phrase(valor, baseFont)).apply {
                border = Rectangle.NO_BORDER
                setPadding(5f)
            }
            tablaMascota.addCell(l)
            tablaMascota.addCell(v)
        }

        agregarFila("Nombre:", mascota.nombre)
        agregarFila("Especie:", mascota.especie.toString())
        agregarFila("Raza:", mascota.raza)
        agregarFila("Fecha Nacimiento:", mascota.fechaNacimiento?.toString() ?: "Sin registro")
        mascota.chipIdentificador?.let { agregarFila("Chip:", it) }
        agregarFila("Tutor:", mascota.tutor.name)
        document.add(tablaMascota)

        // Vacunas
        val tituloVacunas = Paragraph("VACUNAS", sectionFont).apply {
            spacingBefore = 10f
            spacingAfter = 10f
        }
        document.add(tituloVacunas)

        val vacunas = preventivos.filter { it.tipo == TipoPreventivo.VACUNA }
        if (vacunas.isNotEmpty()) {
            val tablaVacunas = PdfPTable(4).apply {
                widthPercentage = 100f
                setWidths(floatArrayOf(3f, 2f, 2f, 2f))
                setSpacingAfter(20f)
            }

            fun addHeader(text: String) {
                tablaVacunas.addCell(PdfPCell(Phrase(text, boldFont)).apply {
                    backgroundColor = Color(220, 220, 220)
                    setPadding(5f)
                    horizontalAlignment = Element.ALIGN_CENTER
                })
            }
            addHeader("Producto")
            addHeader("Fecha Aplicación")
            addHeader("Fecha Refuerzo")
            addHeader("Lote")

            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "CL"))
                .withZone(clinicZoneId)

            vacunas.forEach { v ->
                fun addCell(text: String) = tablaVacunas.addCell(PdfPCell(Phrase(text, baseFont)).apply { setPadding(5f) })
                addCell(v.producto)
                addCell(formatter.format(v.fechaAplicacion))
                addCell(v.fechaRefuerzo?.let { formatter.format(it) } ?: "-")
                addCell(v.lote ?: "-")
            }
            document.add(tablaVacunas)
        } else {
            document.add(Paragraph("No se han registrado vacunas", smallFont).apply { spacingAfter = 20f })
        }

        // Desparasitaciones
        val tituloDesp = Paragraph("DESPARASITACIONES", sectionFont).apply {
            spacingBefore = 10f
            spacingAfter = 10f
        }
        document.add(tituloDesp)

        val desparasitaciones = preventivos.filter { 
            it.tipo == TipoPreventivo.DESPARASITACION_INTERNA || it.tipo == TipoPreventivo.DESPARASITACION_EXTERNA 
        }

        if (desparasitaciones.isNotEmpty()) {
            val tablaDesp = PdfPTable(4).apply {
                widthPercentage = 100f
                setWidths(floatArrayOf(2f, 3f, 2f, 2f))
                setSpacingAfter(20f)
            }

            fun addHeader(text: String) {
                tablaDesp.addCell(PdfPCell(Phrase(text, boldFont)).apply {
                    backgroundColor = Color(220, 220, 220)
                    setPadding(5f)
                    horizontalAlignment = Element.ALIGN_CENTER
                })
            }
            addHeader("Tipo")
            addHeader("Producto")
            addHeader("Fecha Aplicación")
            addHeader("Fecha Refuerzo")

            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "CL"))
                .withZone(clinicZoneId)

            desparasitaciones.forEach { d ->
                fun addCell(text: String) = tablaDesp.addCell(PdfPCell(Phrase(text, baseFont)).apply { setPadding(5f) })
                addCell(when(d.tipo) {
                    TipoPreventivo.DESPARASITACION_INTERNA -> "Interna"
                    TipoPreventivo.DESPARASITACION_EXTERNA -> "Externa"
                    else -> d.tipo.toString()
                })
                addCell(d.producto)
                addCell(formatter.format(d.fechaAplicacion))
                addCell(d.fechaRefuerzo?.let { formatter.format(it) } ?: "-")
            }
            document.add(tablaDesp)
        } else {
            document.add(Paragraph("No se han registrado desparasitaciones", smallFont).apply { spacingAfter = 20f })
        }

        // Próximos Refuerzos
        val ahora = Instant.now()
        val proximos = preventivos
            .filter { it.fechaRefuerzo != null && it.fechaRefuerzo!! > ahora }
            .sortedBy { it.fechaRefuerzo }

        if (proximos.isNotEmpty()) {
            document.add(Paragraph("PRÓXIMOS REFUERZOS", sectionFont).apply {
                spacingBefore = 10f
                spacingAfter = 10f
            })

            val tablaRef = PdfPTable(3).apply {
                widthPercentage = 100f
                setWidths(floatArrayOf(3f, 2f, 2f))
                setSpacingAfter(20f)
            }

            fun addHeader(text: String) {
                tablaRef.addCell(PdfPCell(Phrase(text, boldFont)).apply {
                    backgroundColor = Color(220, 220, 220)
                    setPadding(5f)
                    horizontalAlignment = Element.ALIGN_CENTER
                })
            }
            addHeader("Producto")
            addHeader("Fecha Programada")
            addHeader("Días Restantes")

            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "CL"))
                .withZone(clinicZoneId)

            proximos.forEach { p ->
                val dias = ChronoUnit.DAYS.between(ahora, p.fechaRefuerzo)
                val alerta = dias <= 7
                val fontToUse = if (alerta) alertFont else baseFont
                val bg = if (alerta) Color(255, 255, 200) else null

                fun addCell(text: String) {
                    tablaRef.addCell(PdfPCell(Phrase(text, fontToUse)).apply {
                        setPadding(5f)
                        if (bg != null) backgroundColor = bg
                    })
                }
                addCell(p.producto)
                addCell(formatter.format(p.fechaRefuerzo))
                addCell("$dias días" + if (alerta) " ⚠" else "")
            }
            document.add(tablaRef)

            document.add(Paragraph("⚠ Los refuerzos marcados en amarillo requieren atención en 7 días o menos", smallFont).apply {
                spacingAfter = 10f
            })
        }

        // Footer
        val fechaEmision = Paragraph(
            "Documento generado el ${DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "CL")).withZone(clinicZoneId).format(Instant.now())}",
            smallFont
        ).apply {
            alignment = Element.ALIGN_CENTER
            spacingBefore = 30f
        }
        document.add(fechaEmision)

        document.close()
        return outputStream.toByteArray()
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

    private fun addVaccineSection(document: Document, nombreVacuna: String?, fechaProxima: java.time.LocalDate?) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f
        table.setSpacingBefore(10f)
        table.setSpacingAfter(10f)

        val cell = PdfPCell().apply {
            border = Rectangle.BOX
            borderColor = Color(0, 100, 0)
            borderWidth = 1.5f
            backgroundColor = Color(240, 255, 240)
            setPadding(10f)
            horizontalAlignment = Element.ALIGN_CENTER
        }

        cell.addElement(Paragraph("PLAN DE VACUNACIÓN", vaccineFont).apply { alignment = Element.ALIGN_CENTER })

        val detalle = StringBuilder()
        nombreVacuna?.let { detalle.append("Vacuna administrada: $it\n") }
        fechaProxima?.let {
            val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            detalle.append("PRÓXIMA DOSIS: ${it.format(fmt)}")
        }

        cell.addElement(Paragraph(detalle.toString(), boldFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingBefore = 5f
        })

        table.addCell(cell)
        document.add(table)
    }

    private fun addSignature(document: Document, authorName: String) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f
        table.setSpacingBefore(40f)
        table.setSpacingAfter(10f)

        val cell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_CENTER
        }

        cell.addElement(Paragraph("__________________________", baseFont).apply { alignment = Element.ALIGN_CENTER })
        cell.addElement(Paragraph(authorName.uppercase(), boldFont).apply { alignment = Element.ALIGN_CENTER })
        cell.addElement(Paragraph("Médico Veterinario", baseFont).apply { alignment = Element.ALIGN_CENTER })

        table.addCell(cell)
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

    private fun addVitalSignsSection(document: Document, ficha: cl.clinipets.veterinaria.historial.domain.FichaClinica) {
        val table = PdfPTable(4)
        table.widthPercentage = 100f
        table.setSpacingBefore(5f)
        table.setSpacingAfter(10f)

        fun addCell(label: String, value: String?, color: Color = Color(245, 249, 252)) {
            val phrase = Phrase().apply {
                add(Phrase("$label\n", smallFont))
                add(Phrase(value ?: "---", boldFont))
            }
            table.addCell(PdfPCell(phrase).apply {
                backgroundColor = color
                setPadding(6f)
                horizontalAlignment = Element.ALIGN_CENTER
                border = Rectangle.BOX
                borderColor = Color(220, 230, 240)
            })
        }

        val tempColor = if (ficha.signosVitales.alertaVeterinaria) Color(255, 235, 235) else Color(245, 249, 252)

        addCell("Temperatura", ficha.signosVitales.temperatura?.let { "$it°C" }, tempColor)
        addCell("Frec. Cardíaca", ficha.signosVitales.frecuenciaCardiaca?.let { "$it lpm" })
        addCell("Frec. Resp.", ficha.signosVitales.frecuenciaRespiratoria?.let { "$it rpm" })
        addCell("Peso", ficha.signosVitales.pesoRegistrado?.let { "$it kg" })

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

        fun addRow(label: String, amount: BigDecimal, highlight: Boolean = false) {
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
            .firstOrNull { it.tipo == MediaType.IMAGE } ?: run {
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
        return citas.firstOrNull { it.fechaHoraInicio.atZone(clinicZoneId).toLocalDate() == fechaLocal } ?: citas.firstOrNull()
    }

    private fun buildFinancialSummary(cita: Cita?, fallbackLabel: String): FinancialSummary {
        if (cita == null) {
            return FinancialSummary(
                servicioNombre = fallbackLabel,
                servicioPrecio = BigDecimal.ZERO,
                insumosExtras = BigDecimal.ZERO,
                totalPagado = BigDecimal.ZERO,
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
            cita.detalles.fold(BigDecimal.ZERO) { acc, d -> acc.add(d.precioUnitario) }
        }

        val extras = cita.precioFinal.subtract(subtotalServicios).max(BigDecimal.ZERO)

        // Si está finalizada, se pagó todo. Si no (Confirmada), no se ha pagado nada.
        val pagado = if (cita.estado == EstadoCita.FINALIZADA) cita.precioFinal else BigDecimal.ZERO

        return FinancialSummary(
            servicioNombre = serviciosLabel,
            servicioPrecio = subtotalServicios,
            insumosExtras = extras,
            totalPagado = pagado,
            estadoCita = cita.estado
        )
    }

    private fun formatCurrency(amount: BigDecimal): String {
        val formatter = java.text.NumberFormat.getInstance(Locale.of("es", "CL")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
        return "$$${formatter.format(amount)}"
    }
}

// ----------------------------------------------------------- 
// HELPER PARA FUENTES (CORREGIDO)
// ----------------------------------------------------------- 
object FontFactoryHelper {
    // FIX: Usar BaseFont.WINANSI (CP1252) en lugar de IDENTITY_H para fuentes estándar.
    // Esto soporta tildes y eñes sin requerir un archivo .ttf externo.

    fun base(size: Float, color: Color = Color(40, 40, 40)): Font =
        com.lowagie.text.FontFactory.getFont(
            com.lowagie.text.FontFactory.HELVETICA,
            BaseFont.WINANSI, // <--- CAMBIO CLAVE AQUÍ
            true,
            size,
            Font.NORMAL,
            color
        )

    fun bold(size: Float, color: Color = Color(30, 30, 30)): Font =
        com.lowagie.text.FontFactory.getFont(
            com.lowagie.text.FontFactory.HELVETICA_BOLD,
            BaseFont.WINANSI, // <--- CAMBIO CLAVE AQUÍ
            true,
            size,
            Font.BOLD,
            color
        )
}

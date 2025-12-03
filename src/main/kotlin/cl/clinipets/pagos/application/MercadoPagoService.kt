package cl.clinipets.pagos.application

import com.mercadopago.MercadoPagoConfig
import com.mercadopago.client.payment.PaymentClient
import com.mercadopago.client.preference.PreferenceBackUrlsRequest
import com.mercadopago.client.preference.PreferenceClient
import com.mercadopago.client.preference.PreferenceItemRequest
import com.mercadopago.client.preference.PreferenceRequest
import com.mercadopago.net.MPSearchRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal

interface PagoService {
    fun crearPreferencia(titulo: String, precio: Int, externalReference: String): String
    fun consultarEstadoPago(externalReference: String): EstadoPagoResult
    fun reembolsar(paymentId: Long): Boolean
}

data class EstadoPagoResult(val estado: EstadoPagoMP, val paymentId: Long? = null)

enum class EstadoPagoMP {
    APROBADO,
    OTRO
}

@Component
class MercadoPagoService(
    @Value("\${mercadopago.access-token}") private val accessToken: String
) : PagoService {

    private val logger = LoggerFactory.getLogger(MercadoPagoService::class.java)

    init {
        require(accessToken.isNotBlank()) { "mercadopago.access-token no está configurado" }
        MercadoPagoConfig.setAccessToken(accessToken)
    }

    override fun crearPreferencia(titulo: String, precio: Int, externalReference: String): String {
        logger.info("Creando preferencia MP - Titulo: {}, Precio: {}, Ref: {}", titulo, precio, externalReference)

        val client = PreferenceClient()

        val item = PreferenceItemRequest.builder()
            .title(titulo)
            .quantity(1)
            .unitPrice(BigDecimal.valueOf(precio.toDouble()))
            .currencyId("CLP")
            .build()

        val backUrls = PreferenceBackUrlsRequest.builder()
            .success("clinipets://payment-result?status=success")
            .failure("clinipets://payment-result?status=failure")
            .pending("clinipets://payment-result?status=pending")
            .build()

        val request = PreferenceRequest.builder()
            .items(listOf(item))
            .externalReference(externalReference)
            .backUrls(backUrls)
            .autoReturn("approved")
            .build()

        val preference = client.create(request)
        logger.info("Preferencia MP creada. InitPoint: {}", preference.initPoint)
        return preference.initPoint
    }

    override fun consultarEstadoPago(externalReference: String): EstadoPagoResult {
        if (externalReference.isBlank()) return EstadoPagoResult(EstadoPagoMP.OTRO)

        return try {
            val client = PaymentClient()

            val filters = HashMap<String, Any>()
            filters["external_reference"] = externalReference

            // FIX: Agregamos limit(1) y offset(0) explícitamente para evitar NPE en el SDK
            val searchRequest = MPSearchRequest.builder()
                .filters(filters)
                .limit(10)
                .offset(0)
                .build()

            val searchResults = client.search(searchRequest)

            val payments = searchResults.results ?: emptyList()

            // Log para debug
            if (payments.isEmpty()) {
                logger.debug("[Auto-Healing] Búsqueda retornó 0 pagos para Ref: {}", externalReference)
            }

            val pagoAprobado = payments.firstOrNull { payment ->
                "approved".equals(payment.status, ignoreCase = true)
            }

            if (pagoAprobado != null) {
                logger.info("[Auto-Healing] Pago encontrado y APROBADO. ID: {}, Ref: {}", pagoAprobado.id, externalReference)
                EstadoPagoResult(EstadoPagoMP.APROBADO, pagoAprobado.id)
            } else {
                EstadoPagoResult(EstadoPagoMP.OTRO)
            }

        } catch (e: Exception) {
            // El log mostrará la excepción real si vuelve a ocurrir, pero no tumbará la app
            logger.error("[Auto-Healing] Error al consultar MercadoPago para Ref: {}", externalReference, e)
            EstadoPagoResult(EstadoPagoMP.OTRO)
        }
    }

    override fun reembolsar(paymentId: Long): Boolean {
        return try {
            val client = PaymentClient()
            client.refund(paymentId) // Método nativo del SDK
            logger.info("Reembolso exitoso para pago $paymentId")
            true
        } catch (e: Exception) {
            logger.error("Error al reembolsar pago $paymentId", e)
            false
        }
    }
}
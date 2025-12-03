package cl.clinipets.pagos.application

import com.mercadopago.MercadoPagoConfig
import com.mercadopago.client.preference.PreferenceBackUrlsRequest
import com.mercadopago.client.preference.PreferenceClient
import com.mercadopago.client.preference.PreferenceItemRequest
import com.mercadopago.client.preference.PreferenceRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal

interface PagoService {
    fun crearPreferencia(titulo: String, precio: Int, externalReference: String): String
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

        logger.info("Preferencia MP creada exitosamente. InitPoint: {}", preference.initPoint)

        return preference.initPoint
    }
}

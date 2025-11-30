package cl.clinipets.backend.pagos.application

import com.mercadopago.MercadoPagoConfig
import com.mercadopago.client.preference.PreferenceClient
import com.mercadopago.client.preference.PreferenceItemRequest
import com.mercadopago.client.preference.PreferenceRequest
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

    init {
        require(accessToken.isNotBlank()) { "mercadopago.access-token no está configurado" }
        MercadoPagoConfig.setAccessToken(accessToken)
    }

    override fun crearPreferencia(titulo: String, precio: Int, externalReference: String): String {
        val client = PreferenceClient()
        val item = PreferenceItemRequest.builder()
            .title(titulo)
            .quantity(1)
            .unitPrice(BigDecimal.valueOf(precio.toDouble()))
            .currencyId("CLP")
            .build()
        val request = PreferenceRequest.builder()
            .items(listOf(item))
            .externalReference(externalReference)
            .build()
        val preference = client.create(request)
        return preference.initPoint
    }
}

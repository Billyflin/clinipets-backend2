package cl.clinipets.backend.pagos.infraestructura

import com.mercadopago.client.preference.PreferenceBackUrlsRequest
import com.mercadopago.client.preference.PreferenceClient
import com.mercadopago.client.preference.PreferenceItemRequest
import com.mercadopago.client.preference.PreferenceRequest
import com.mercadopago.resources.preference.Preference
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class MercadoPagoService {

    fun crearPreferencia(titulo: String, precio: BigDecimal, idReserva: Long): String {
        // Crear Item
        val itemRequest = PreferenceItemRequest.builder()
            .title(titulo)
            .quantity(1)
            .unitPrice(precio)
            .currencyId("CLP")
            .build()

        // Configurar Back URLs (dummy por ahora)
        val backUrls = PreferenceBackUrlsRequest.builder()
            .success("https://clinipets.cl/pago/exito")
            .failure("https://clinipets.cl/pago/fallo")
            .pending("https://clinipets.cl/pago/pendiente")
            .build()

        // Crear Preferencia
        val preferenceRequest = PreferenceRequest.builder()
            .items(listOf(itemRequest))
            .externalReference(idReserva.toString())
            .backUrls(backUrls)
            .autoReturn("approved") // Opcional: Retorno automático
            .build()

        // Cliente
        val client = PreferenceClient()
        val preference: Preference = client.create(preferenceRequest)

        return preference.initPoint
    }
}

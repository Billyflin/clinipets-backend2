package cl.clinipets.backend.pagos.config

import com.mercadopago.MercadoPagoConfig
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class MercadoPagoConfiguration {

    @Value("\${mercadopago.access-token}")
    private lateinit var accessToken: String

    @PostConstruct
    fun init() {
        MercadoPagoConfig.setAccessToken(accessToken)
    }
}

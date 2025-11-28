package cl.clinipets.backend.identidad.aplicacion

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleTokenVerifier(
    @Value("\${google.client-id}") private val clientId: String
) {
    private val transport = GoogleNetHttpTransport.newTrustedTransport()
    private val json = JacksonFactory.getDefaultInstance()
    private val verifier = GoogleIdTokenVerifier.Builder(transport, json)
        .setAudience(listOf(clientId))
        .build()

    fun verify(idTokenString: String): GoogleIdToken.Payload {
        val idToken = verifier.verify(idTokenString) ?: error("ID token inválido")
        val payload = idToken.payload
        val iss = payload.issuer
        require(iss == "accounts.google.com" || iss == "https://accounts.google.com") { "Issuer no válido" }
        return payload
    }
}

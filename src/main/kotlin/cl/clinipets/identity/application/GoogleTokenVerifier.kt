package cl.clinipets.identity.application

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleTokenVerifier(
    @Value("\${google.client-id}") private val clientId: String
) {
    private val verifier: GoogleIdTokenVerifier by lazy {
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), JacksonFactory.getDefaultInstance())
            .setAudience(listOf(clientId))
            .build()
    }

    fun verify(idToken: String): GoogleIdToken.Payload? =
        verifier.verify(idToken)?.payload
}

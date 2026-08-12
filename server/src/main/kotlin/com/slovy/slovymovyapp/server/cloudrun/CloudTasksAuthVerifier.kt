package com.slovy.slovymovyapp.server.cloudrun

import com.google.auth.oauth2.TokenVerifier
import io.ktor.util.logging.KtorSimpleLogger

/**
 * Verifies OIDC tokens from Cloud Tasks requests.
 */
object CloudTasksAuthVerifier {
    private val logger = KtorSimpleLogger("CloudTasksAuthVerifier")
    private val tokenVerifier: TokenVerifier by lazy {
        val audience = CloudRunMetadata.getDeterministicServiceUrl()
        TokenVerifier.newBuilder()
            .setAudience(audience)
            .build()
    }

    /**
     * Verify an OIDC token from a Cloud Tasks request.
     *
     * @param authorizationHeader The Authorization header value (e.g., "Bearer eyJ...")
     * @return true if the token is valid, false otherwise
     */
    fun verify(authorizationHeader: String?): Boolean {
        if (authorizationHeader == null) return false
        if (!authorizationHeader.startsWith("Bearer ", ignoreCase = true)) {
            // Never log the header itself: whatever scheme it carries, the value is a credential.
            logger.warn("Authorization header does not start with Bearer")
            return false
        }

        val token = authorizationHeader.removePrefix("Bearer ").removePrefix("bearer ")

        return try {
            tokenVerifier.verify(token)
            true
        } catch (e: TokenVerifier.VerificationException) {
            logger.warn("Failed to verify token, $e")
            false
        } catch (e: Exception) {
            logger.error("Unexpected error verifying token", e)
            false
        }
    }
}

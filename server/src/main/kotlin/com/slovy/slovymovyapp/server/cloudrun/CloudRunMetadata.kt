package com.slovy.slovymovyapp.server.cloudrun

import com.google.auth.oauth2.ComputeEngineCredentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.MetadataConfig
import com.google.cloud.MetadataConfig.getZone
import io.ktor.util.logging.KtorSimpleLogger

/**
 * Utility for accessing Cloud Run metadata server.
 */
object CloudRunMetadata {
    private const val K_SERVICE = "K_SERVICE"
    private val logger = KtorSimpleLogger("CloudRunMetadata")

    private val credentials: GoogleCredentials by lazy {
        GoogleCredentials.getApplicationDefault()
    }

    fun getServiceAccountEmail(): String {
        val creds = credentials
        return if (creds is ComputeEngineCredentials) {
            creds.account
        } else {
            logger.warn("Credentials are not ComputeEngineCredentials")
            throw IllegalStateException("Not running on Cloud Run or unable to retrieve credentials")
        }
    }

    fun getDeterministicServiceUrl(): String {
        val projectNumber = MetadataConfig.getAttribute("project/numeric-project-id")
        val region = getZone()
        val serviceName = System.getenv(K_SERVICE)
        return "https://$serviceName-$projectNumber.$region.run.app"
    }
}

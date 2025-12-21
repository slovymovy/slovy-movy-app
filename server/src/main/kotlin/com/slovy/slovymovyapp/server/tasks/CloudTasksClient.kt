package com.slovy.slovymovyapp.server.tasks

import com.google.cloud.MetadataConfig.getProjectId
import com.google.cloud.MetadataConfig.getZone
import com.google.cloud.tasks.v2.*
import com.google.protobuf.ByteString
import com.slovy.slovymovyapp.server.cloudrun.CloudRunMetadata
import com.slovy.slovymovyapp.updateRepoPath
import io.ktor.util.logging.KtorSimpleLogger

/**
 * Client for queueing async tasks via Google Cloud Tasks.
 */
object RepoUpdateTaskClient {
    private val logger = KtorSimpleLogger("RepoUpdateTaskClient")

    private const val ENV_QUEUE = "CLOUD_TASKS_QUEUE"
    private const val DEFAULT_QUEUE = "repo-updates"

    private val client: CloudTasksClient by lazy {
        CloudTasksClient.create()
    }

    /**
     * Queues a task to update the repository with processed word data.
     *
     * @param lang Language code (e.g., "en")
     * @param word The word that was processed
     * @param responseJson The LanguageCardResponse JSON to store
     */
    fun queueRepoUpdate(lang: String, word: String, responseJson: String) {
        val projectId = getProjectId()
        val location = getZone()
        val queue = getQueueName()
        val serviceUrl = CloudRunMetadata.getDeterministicServiceUrl()
        val serviceAccount = CloudRunMetadata.getServiceAccountEmail()
        logger.info("Using service account $serviceAccount")

        val url = "$serviceUrl$updateRepoPath$lang/$word"
        logger.info("Queuing task for $url")

        val queuePath = QueueName.of(projectId, location, queue).toString()
        logger.info("Using queue $queuePath")

        val httpRequestBuilder = HttpRequest.newBuilder()
            .setUrl(url)
            .setHttpMethod(HttpMethod.POST)
            .putHeaders("Content-Type", "application/json")
            .setBody(ByteString.copyFromUtf8(responseJson))

        // Add OIDC token for authentication
        httpRequestBuilder.oidcToken = OidcToken.newBuilder()
            .setServiceAccountEmail(serviceAccount)
            .setAudience(serviceUrl)
            .build()

        val task = Task.newBuilder()
            .setHttpRequest(httpRequestBuilder.build())
            .build()

        client.createTask(queuePath, task)
    }

    private fun getQueueName(): String {
        return System.getenv(ENV_QUEUE)?.takeIf { it.isNotBlank() } ?: DEFAULT_QUEUE
    }
}

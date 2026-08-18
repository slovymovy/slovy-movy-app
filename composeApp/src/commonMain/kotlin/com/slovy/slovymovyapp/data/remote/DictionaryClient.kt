package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.api.WordStreamChunk
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.use
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.api.WordStreamStage
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.logging.AppLogger
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Result wrapper for progressive word loading.
 * Emitted multiple times as data becomes available.
 */
data class WordResult(
    /** The current LanguageCard data, null if no data available (error-only emission) */
    val card: LanguageCard? = null,
    /** True if base word content is still being fetched (for online-only words) */
    val isWordLoading: Boolean = false,
    /** True if translations are still being fetched */
    val isTranslationLoading: Boolean = false,
    /** Error (fatal if card=null, non-fatal if card present) */
    val error: DictionaryClientException? = null
) {
    /** True if we have usable data */
    val hasData: Boolean get() = card != null

    /** True if this is an error-only result with no data */
    val isErrorOnly: Boolean get() = card == null && error != null
}

/**
 * Exception hierarchy for DictionaryClient errors.
 */
sealed class DictionaryClientException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Raw data for the word doesn't exist locally - cannot fetch from server */
    class RawDataMissingException(val lemma: String, val language: Language) :
        DictionaryClientException("Raw data for '$lemma' (${language.code}) not found locally")

    /** Network or connection error */
    class NetworkException(message: String, cause: Throwable? = null) :
        DictionaryClientException(message, cause)

    /** Server returned an error response */
    class ServerException(val statusCode: Int, val body: String) :
        DictionaryClientException("Server error $statusCode: ${body.take(200)}")

    /** Operation was cancelled */
    class CancelledException(message: String = "Operation cancelled") :
        DictionaryClientException(message)
}

/**
 * Client for fetching word data with progressive updates.
 *
 * Provides word data through `Flow<WordResult>` that emits progressively as data becomes available:
 * 1. If complete local data exists → single emission
 * 2. If data needs remote fetch → emits local first (if available), then server responses
 *
 * Server responses are ingested into the local database before emission.
 */
class DictionaryClient(
    private val platform: PlatformDbSupport,
    private val dictionaryRepository: DictionaryRepository,
    private val localDbManager: LocalDbManager,
    private val dataDbManager: DataDbManager,
    baseUrl: String = PRODUCTION_SERVER_URL
) {
    companion object {
        const val PRODUCTION_SERVER_URL = "https://backend.openwords.ai"
        private const val TAG = "DictionaryClient"
    }

    private val serverBaseUrl: String = baseUrl.trimEnd('/')

    private val httpClient: HttpClient by lazy {
        platform.createHttpClient().config {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
        }
    }

    // Server may have newer trait types, so ignore unknown keys
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FeedbackRequest(val comment: String, val email: String? = null)

    @Serializable
    data class FeedbackResponse(val issueUrl: String)

    @Serializable
    data class GeneralFeedbackResponse(val discussionUrl: String)

    /**
     * Fetches word data with progressive updates.
     *
     * @param language The source language
     * @param lemma The word to fetch
     * @param translationTargets Target languages for translations (empty = no translations)
     * @param pushToRepo Whether to push changes to the repository
     * @return Flow that emits WordResult with progressive updates
     */
    fun getWord(
        language: Language,
        lemma: String,
        translationTargets: List<Language> = emptyList(),
        pushToRepo: Boolean = false
    ): Flow<WordResult> = flow {
        PerformanceMonitoring.startTrace("dictionary_client_get_word").useWithResult {
            putAttributes(
                mapOf(
                    "lang" to language.code,
                    "target_count" to translationTargets.size,
                ),
            )
            var emissionCount = 0L

            suspend fun emitMeasured(wordResult: WordResult) {
                emissionCount += 1
                emit(wordResult)
            }

            try {
                // 1. Try local first
                val localCard = dictionaryRepository.getLanguageCard(language, lemma, translationTargets)
                if (localCard == null) {
                    markResult("raw_data_missing")
                    emitMeasured(
                        WordResult(
                            card = localCard,
                            isTranslationLoading = false,
                            error = DictionaryClientException.RawDataMissingException(lemma, language)
                        )
                    )
                    return@flow
                }
                val needsRemote = needsRemoteFetch(localCard, translationTargets)
                val needsTranslations = translationTargets.isNotEmpty() &&
                        hasMissingTranslations(localCard, translationTargets)
                putAttributes(
                    mapOf(
                        "remote_fetch" to needsRemote,
                        "online_only" to localCard.online,
                        "needs_translations" to needsTranslations,
                    ),
                )

                if (!needsRemote) {
                    markResult("local_hit")
                    // Complete local data - single emission, no loading
                    emitMeasured(WordResult(card = localCard, isTranslationLoading = false))
                    return@flow
                }

                // 2. Always emit local first with loading indicator (shows POS structure while fetching)
                emitMeasured(
                    WordResult(
                        card = localCard,
                        isWordLoading = localCard.online,
                        isTranslationLoading = needsTranslations
                    )
                )

                // 3. Stream from server - emits WordResult after EACH stage
                try {
                    val measuredCollector = object : FlowCollector<WordResult> {
                        override suspend fun emit(value: WordResult) {
                            emitMeasured(value)
                        }
                    }
                    streamFromServer(
                        measuredCollector,
                        language,
                        lemma,
                        translationTargets,
                        pushToRepo,
                        localCard.zipfFrequency.toDouble(),
                        localCard.online
                    )
                } catch (e: CancellationException) {
                    markResult("remote_cancelled")
                    AppLogger.warn(TAG, "getWord cancelled for $language/$lemma", e)
                    // Emit error for cancellation, then re-throw to propagate
                    emitMeasured(
                        WordResult(
                            card = localCard,
                            isWordLoading = false,
                            isTranslationLoading = false,
                            error = DictionaryClientException.CancelledException()
                        )
                    )
                    throw e
                } catch (e: Exception) {
                    markResult("remote_error")
                    AppLogger.error(TAG, "getWord remote fetch failed for $language/$lemma", e)
                    // Emit error result - preserve loading context so UI knows where error occurred
                    emitMeasured(
                        WordResult(
                            card = localCard,
                            isWordLoading = false,
                            isTranslationLoading = false,
                            error = wrapException(e)
                        )
                    )
                }
            } finally {
                putMetric("emissions", emissionCount)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun needsRemoteFetch(card: LanguageCard, targets: List<Language>): Boolean {
        // If the card was built from online_only data, we need to fetch full content
        // Check if any target translations are missing
        return card.online || hasMissingTranslations(card, targets)
    }

    private fun hasMissingTranslations(card: LanguageCard?, targets: List<Language>): Boolean {
        if (card == null || targets.isEmpty()) return false

        // Check if any target languages are missing from the card's translations
        val existingLanguages = card.entries
            .flatMap { it.senses }
            .flatMap { sense -> sense.translations.keys + sense.targetLangDefinitions.keys }
            .toSet()

        return targets.any { it !in existingLanguages }
    }

    private fun wrapException(e: Exception): DictionaryClientException {
        return when (e) {
            is DictionaryClientException -> e
            else -> DictionaryClientException.NetworkException(
                NetworkErrorClassifier.userMessage(e),
                e
            )
        }
    }

    /**
     * Copies raw data from downloaded DB to local DB for online_only words.
     * This version takes the localDb and downloadedDb as parameters for use within an existing transaction.
     *
     * @param posFilter If provided, only copies raw data for these POS (to avoid empty entries)
     * Idempotent - skips if lemma already exists in local DB.
     */
    private fun copyRawDataIfNeeded(
        ingestionBuilder: JsonIngestionBuilder,
        language: Language,
        lemma: String,
        frequency: Double,
        targetDb: DictionaryDatabase,
        sourceDb: DictionaryDatabase,
        posFilter: Set<String>? = null
    ) {
        val lemmaId = JsonIngestionBuilder.generateLemmaId(lemma)

        // Skip if already in local DB
        if (targetDb.dictionaryQueries.selectLemmasById(lemmaId).executeAsOneOrNull() != null) {
            return
        }

        // Copy from downloaded DB
        ingestionBuilder.copyRawDataToLocal(
            word = lemma,
            langCode = language.code,
            sourceDb = sourceDb,
            targetDb = targetDb,
            frequency = frequency,
            posFilter = posFilter
        )
    }

    /**
     * Finds the dictionary DB that contains the fully processed word (with senses).
     * Tries local DB first (word may have been just ingested), then downloaded DB.
     *
     * Used for translation ingestion - we need to read sense IDs from the dictionary
     * where the word exists with full data.
     */
    /**
     * Locates a dictionary DB that contains [lemma] with full data (not online_only), and runs
     * [block] while holding a read lease on the chosen DB. Tries the local DB first (recently
     * ingested words), then the downloaded DB.
     *
     * The inner lambda returns `Result.success(...)` to signal "I ran the block, here's the
     * value" — `null` means "this DB doesn't have the entry, try the next one". `Result` is used
     * (rather than a custom wrapper) because [T] may itself be nullable, which would make a bare
     * `T?` ambiguous.
     */
    private suspend fun <T> withDictionaryDbContainingWord(
        language: Language,
        lemma: String,
        block: suspend (DictionaryDatabase) -> T,
    ): T {
        val lemmaId = JsonIngestionBuilder.generateLemmaId(lemma)

        // Try local first (existence check happens inside the lease — no TOCTOU).
        val localOutcome = localDbManager.withLocalDictionaryIfExists { localDb ->
            if (localDb == null) return@withLocalDictionaryIfExists null
            val row = localDb.dictionaryQueries.selectLemmasById(lemmaId).executeAsOneOrNull()
            if (row != null && !row.online_only) Result.success(block(localDb)) else null
        }
        if (localOutcome != null) return localOutcome.getOrThrow()

        val downloadedOutcome = dataDbManager.withDictionaryReadOnlyIfExists(language) { downloadedDb ->
            if (downloadedDb == null) return@withDictionaryReadOnlyIfExists null
            val row = downloadedDb.dictionaryQueries.selectLemmasById(lemmaId).executeAsOneOrNull()
            if (row != null && !row.online_only) Result.success(block(downloadedDb)) else null
        }
        if (downloadedOutcome != null) return downloadedOutcome.getOrThrow()

        throw IllegalStateException("Word '$lemma' not found with senses in any dictionary")
    }

    private suspend fun streamFromServer(
        collector: FlowCollector<WordResult>,
        language: Language,
        lemma: String,
        targets: List<Language>,
        push: Boolean,
        frequency: Double,
        localIsOnlineOnly: Boolean
    ) {
        PerformanceMonitoring.startTrace("dictionary_client_stream").useWithResult {
            putAttributes(
                mapOf(
                    "lang" to language.code,
                    "target_count" to targets.size,
                    "push_to_repo" to push,
                    "online_only" to localIsOnlineOnly,
                ),
            )
            var chunkCount = 0L
            try {
                val url = buildUrl(language, lemma, targets, push)
                val response = httpClient.get(url)
                putMetric("status_code", response.status.value.toLong())
                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    markResult("server_error")
                    throw DictionaryClientException.ServerException(response.status.value, body)
                }

                val channel = response.bodyAsChannel()

                // Read line-by-line, parse each as WordStreamChunk
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break
                    if (line.isBlank()) continue

                    val chunk = json.decodeFromString(WordStreamChunk.serializer(), line)
                    chunkCount += 1
                    val isBaseStage = chunk.stage == WordStreamStage.BASE

                    val haveRequiredTranslations = chunk.payload.entries.any {
                        it.senses.any { s ->
                            s.translations.keys.containsAll(targets.map { k -> k.code })
                        }
                    }
                    // After base, we expect translated if translations were requested
                    val hasMoreChunks = isBaseStage && !haveRequiredTranslations

                    processChunk(
                        collector = collector,
                        chunk = chunk,
                        language = language,
                        lemma = lemma,
                        translationTargets = targets,
                        hasMoreChunks = hasMoreChunks,
                        frequency = frequency,
                        localIsOnlineOnly = localIsOnlineOnly
                    )
                }
            } finally {
                putMetric("chunks", chunkCount)
            }
        }
    }

    private suspend fun processChunk(
        collector: FlowCollector<WordResult>,
        chunk: WordStreamChunk,
        language: Language,
        lemma: String,
        translationTargets: List<Language>,
        hasMoreChunks: Boolean,
        frequency: Double,
        localIsOnlineOnly: Boolean
    ) {
        PerformanceMonitoring.startTrace("dictionary_client_process_chunk").use { trace ->
            trace.putAttributes(
                mapOf(
                    "lang" to language.code,
                    "stage" to chunk.stage.name.lowercase(),
                    "target_count" to translationTargets.size,
                    "online_only" to localIsOnlineOnly,
                ),
            )
            trace.putMetric("entries", chunk.payload.entries.size.toLong())
            trace.putMetric("senses", chunk.payload.entries.sumOf { it.senses.size }.toLong())
            // Hold read leases on both local DBs for the entire ingestion. This means: (1) the local
            // dictionary captured as `localDictDb` and used across multiple call paths is guaranteed
            // not to be torn down mid-flight; (2) the non-suspending `translationDbProvider` callback
            // returns the captured leased translation DB instead of opening a fresh, unleased one.
            localDbManager.withLocalDictionary { localDictDb ->
                localDbManager.withLocalTranslation { localTransDb ->
                    val responseJson = json.encodeToString(LanguageCardResponse.serializer(), chunk.payload)

                    val ingestionBuilder = JsonIngestionBuilder(
                        translationDbProvider = { _, _ -> localTransDb },
                        frequencyMap = mapOf(lemma to frequency),
                        warningLogger = { message -> AppLogger.warn(TAG, message, null) },
                    )

                    when (chunk.stage) {
                        WordStreamStage.BASE -> {
                            // Validate that server returned at least some POS entries
                            if (chunk.payload.entries.isEmpty()) {
                                throw DictionaryClientException.ServerException(
                                    statusCode = 500,
                                    body = "Server returned no POS entries for '$lemma'"
                                )
                            }

                            if (localIsOnlineOnly) {
                                // Online-only word - copy raw data to local DB first, then ingest
                                // processed data over it. The downloaded DB lease covers the whole
                                // transaction so a concurrent delete cannot unlink it mid-write.
                                // Existence is checked inside the IfExists lease (no TOCTOU).
                                dataDbManager.withDictionaryReadOnlyIfExists(language) { downloadedDb ->
                                    localDictDb.transaction {
                                        if (downloadedDb != null) {
                                            val posFilter = chunk.payload.entries.map { it.pos }.toSet()
                                            copyRawDataIfNeeded(
                                                ingestionBuilder = ingestionBuilder,
                                                language = language,
                                                lemma = lemma,
                                                frequency = frequency,
                                                targetDb = localDictDb,
                                                sourceDb = downloadedDb,
                                                posFilter = posFilter
                                            )
                                        }
                                        ingestionBuilder.ingestProcessedOverRaw(
                                            responseJson, lemma, language.code, localDictDb
                                        )
                                    }
                                }
                            } else {
                                // Offline word - find dictionary DB with the processed word,
                                // add translations only (translations go to local translation DB)
                                withDictionaryDbContainingWord(language, lemma) { dictDbForReading ->
                                    ingestionBuilder.ingestTranslationsOnly(
                                        responseJson, lemma, language.code, dictDbForReading
                                    )
                                }
                            }
                        }

                        WordStreamStage.TRANSLATED -> {
                            // Find dictionary DB with the word (could be local after BASE stage, or
                            // downloaded).
                            withDictionaryDbContainingWord(language, lemma) { dictDbForReading ->
                                ingestionBuilder.ingestTranslationsOnly(
                                    responseJson, lemma, language.code, dictDbForReading
                                )
                            }
                        }
                    }
                }
            }

            // Re-read and emit updated card with loading state
            val senseIdsToInvalidate = chunk.payload.entries
                .flatMap { it.senses }
                .map { it.senseId }
                .toSet()
            if (senseIdsToInvalidate.isNotEmpty()) {
                dictionaryRepository.invalidateSenses(senseIdsToInvalidate)
            }

            val updated = dictionaryRepository.getLanguageCard(language, lemma, translationTargets)
            trace.putAttribute("emitted_update", (updated != null).toString())
            updated?.let {
                collector.emit(
                    WordResult(
                        card = it,
                        isTranslationLoading = hasMoreChunks,
                        error = null
                    )
                )
            }
        }
    }

    private fun buildUrl(
        language: Language,
        lemma: String,
        targets: List<Language>,
        push: Boolean
    ): String {
        return buildString {
            append(serverBaseUrl)
            append("/word/")
            append(language.code)
            append("/")
            append(lemma.encodeURLPath())
            if (targets.isNotEmpty()) {
                append("?translations=")
                append(targets.joinToString(",") { it.code })
            }
            if (push) {
                append(if (targets.isEmpty()) "?" else "&")
                append("push=1")
            }
        }
    }

    suspend fun sendFeedback(
        language: Language,
        lemma: String,
        translationTargets: List<Language>,
        comment: String,
        email: String? = null
    ): FeedbackResponse = postFeedback(
        url = buildFeedbackUrl(language, lemma, translationTargets),
        comment = comment,
        email = email,
        responseSerializer = FeedbackResponse.serializer(),
        opLabel = "sendFeedback for $language/$lemma"
    )

    suspend fun sendListSuggestion(
        language: Language,
        comment: String,
        email: String? = null
    ): FeedbackResponse = postFeedback(
        url = "$serverBaseUrl/list-suggestion/${language.code}",
        comment = comment,
        email = email,
        responseSerializer = FeedbackResponse.serializer(),
        opLabel = "sendListSuggestion for ${language.code}"
    )

    suspend fun sendGeneralFeedback(
        comment: String,
        email: String? = null
    ): GeneralFeedbackResponse = postFeedback(
        url = "$serverBaseUrl/feedback",
        comment = comment,
        email = email,
        responseSerializer = GeneralFeedbackResponse.serializer(),
        opLabel = "sendGeneralFeedback"
    )

    /**
     * Posts a [FeedbackRequest] and decodes the response, which is the whole of what the three
     * feedback endpoints do differently only by URL and response shape.
     *
     * Comment and email are trimmed here so every caller sends the same normalized body, and a
     * blank email is dropped rather than sent as an empty string.
     *
     * @param opLabel Names the operation in the logs; it is the only per-caller diagnostic.
     */
    private suspend fun <T> postFeedback(
        url: String,
        comment: String,
        email: String?,
        responseSerializer: DeserializationStrategy<T>,
        opLabel: String
    ): T {
        try {
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                val trimmedEmail = email?.trim()?.takeIf { it.isNotBlank() }
                setBody(
                    json.encodeToString(
                        FeedbackRequest.serializer(),
                        FeedbackRequest(comment.trim(), trimmedEmail)
                    )
                )
            }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                throw DictionaryClientException.ServerException(response.status.value, body)
            }

            val body = response.bodyAsText()
            return json.decodeFromString(responseSerializer, body)
        } catch (e: CancellationException) {
            AppLogger.warn(TAG, "$opLabel cancelled", e)
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "$opLabel failed", e)
            if (e is DictionaryClientException) throw e
            throw DictionaryClientException.NetworkException(
                NetworkErrorClassifier.userMessage(e),
                e
            )
        }
    }

    private fun buildFeedbackUrl(
        language: Language,
        lemma: String,
        targets: List<Language>
    ): String {
        return buildString {
            append(serverBaseUrl)
            append("/feedback/")
            append(language.code)
            append("/")
            append(lemma.encodeURLPath())
            if (targets.isNotEmpty()) {
                append("?translations=")
                append(targets.distinct().joinToString(",") { it.code })
            }
        }
    }
}
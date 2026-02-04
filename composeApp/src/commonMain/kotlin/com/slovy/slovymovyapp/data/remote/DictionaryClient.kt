package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.api.WordStreamChunk
import com.slovy.slovymovyapp.api.WordStreamStage
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
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
        // 1. Try local first
        val localCard = dictionaryRepository.getLanguageCard(language, lemma, translationTargets)
        if (localCard == null) {
            emit(
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

        if (!needsRemote) {
            // Complete local data - single emission, no loading
            emit(WordResult(card = localCard, isTranslationLoading = false))
            return@flow
        }

        // 2. Always emit local first with loading indicator (shows POS structure while fetching)
        emit(
            WordResult(
                card = localCard,
                isWordLoading = localCard.online,
                isTranslationLoading = needsTranslations
            )
        )

        // 3. Stream from server - emits WordResult after EACH stage
        try {
            streamFromServer(
                this,
                language,
                lemma,
                translationTargets,
                pushToRepo,
                localCard.zipfFrequency.toDouble(),
                localCard.online
            )
        } catch (e: CancellationException) {
            // Emit error for cancellation, then re-throw to propagate
            emit(
                WordResult(
                    card = localCard,
                    isWordLoading = false,
                    isTranslationLoading = false,
                    error = DictionaryClientException.CancelledException()
                )
            )
            throw e
        } catch (e: Exception) {
            // Emit error result - preserve loading context so UI knows where error occurred
            emit(
                WordResult(
                    card = localCard,
                    isWordLoading = false,
                    isTranslationLoading = false,
                    error = wrapException(e)
                )
            )
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
    private suspend fun findDictionaryDbWithWord(
        language: Language,
        lemma: String
    ): DictionaryDatabase {
        val lemmaId = JsonIngestionBuilder.generateLemmaId(lemma)

        // Try local first (word may have been just ingested in BASE stage)
        val localDb = localDbManager.openLocalDictionary()
        val localLemma = localDb.dictionaryQueries.selectLemmasById(lemmaId).executeAsOneOrNull()
        if (localLemma != null && !localLemma.online_only) {
            return localDb
        }

        // Try downloaded DB
        if (dataDbManager.hasDictionary(language)) {
            val downloadedDb = dataDbManager.openDictionaryReadOnly(language)
            val downloadedLemma = downloadedDb.dictionaryQueries.selectLemmasById(lemmaId).executeAsOneOrNull()
            if (downloadedLemma != null && !downloadedLemma.online_only) {
                return downloadedDb
            }
        }

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
        val url = buildUrl(language, lemma, targets, push)

        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                throw DictionaryClientException.ServerException(response.status.value, body)
            }

            val channel = response.bodyAsChannel()

            // Read line-by-line, parse each as WordStreamChunk
            var receivedBase = false
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue

                val chunk = json.decodeFromString(WordStreamChunk.serializer(), line)
                val isBaseStage = chunk.stage == WordStreamStage.BASE
                receivedBase = receivedBase || isBaseStage

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
        val localDictDb = localDbManager.openLocalDictionary()
        val responseJson = json.encodeToString(LanguageCardResponse.serializer(), chunk.payload)

        val ingestionBuilder = JsonIngestionBuilder(
            translationDbProvider = { _, _ -> localDbManager.openLocalTranslation() },
            frequencyMap = mapOf(lemma to frequency)
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
                    // Online-only word - copy raw data to local DB first,
                    // then ingest processed data over it.
                    // Open downloaded DB before transaction (suspend call).
                    val downloadedDb = if (dataDbManager.hasDictionary(language)) {
                        dataDbManager.openDictionaryReadOnly(language)
                    } else null

                    // Wrap in transaction to avoid partial state if interrupted.
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
                } else {
                    // Offline word - find dictionary DB with the processed word,
                    // add translations only (translations go to local translation DB)
                    val dictDbForReading = findDictionaryDbWithWord(language, lemma)
                    ingestionBuilder.ingestTranslationsOnly(
                        responseJson, lemma, language.code, dictDbForReading
                    )
                }
            }

            WordStreamStage.TRANSLATED -> {
                // Find dictionary DB with the word (could be local after BASE stage, or downloaded)
                val dictDbForReading = findDictionaryDbWithWord(language, lemma)
                ingestionBuilder.ingestTranslationsOnly(
                    responseJson, lemma, language.code, dictDbForReading
                )
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
}

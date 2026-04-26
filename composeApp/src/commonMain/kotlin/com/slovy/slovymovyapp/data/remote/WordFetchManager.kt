package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Manages word fetching with app-scoped lifecycle.
 *
 * This class ensures that word fetches continue even if the requesting ViewModel
 * is cleared (e.g., user navigates away). Active fetches are cached so that
 * multiple requests for the same word reuse the same ongoing fetch.
 *
 * Key design decisions:
 * - Uses MutableSharedFlow with replay=1 so late subscribers get the latest value
 * - Tracks completion state to avoid returning stale flows to new requests
 * - Completed fetches are removed so new requests start fresh (data is in DB)
 */
class WordFetchManager(
    private val dictionaryClient: DictionaryClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeFetches = mutableMapOf<FetchKey, FetchEntry>()
    private val mutex = Mutex()

    private data class FetchKey(
        val language: Language,
        val lemma: String,
        val translationsKey: String,
        val pushToRepo: Boolean
    )

    private class FetchEntry(
        val flow: MutableSharedFlow<WordResult>,
        @Volatile var isComplete: Boolean = false
    )

    /**
     * Gets word data, reusing an active fetch if one exists.
     *
     * The returned Flow continues running in the background even if the collector cancels.
     * Once the fetch completes (success or error), it's marked complete and will be
     * removed on the next request for this word. Data is already in the local DB at that point.
     */
    suspend fun getWord(
        language: Language,
        lemma: String,
        translationTargets: List<Language>,
        pushToRepo: Boolean = false
    ): Flow<WordResult> = mutex.withLock {
        val normalizedLemma = lemma.trim().lowercase()
        // Normalize translations to make cache key deterministic
        val normalizedTargets = translationTargets.distinct().sortedBy { it.code }
        val key = FetchKey(
            language = language,
            lemma = normalizedLemma,
            translationsKey = normalizedTargets.joinToString(",") { it.code },
            pushToRepo = pushToRepo
        )

        // Remove all completed entries - map is small (usually 1-2 inflight)
        val completedKeys = activeFetches.filterValues { it.isComplete }.keys.toList()
        completedKeys.forEach { completedKey ->
            activeFetches.remove(completedKey)
        }

        val existing = activeFetches[key]
        if (existing != null) {
            // Return existing active flow
            return@withLock existing.flow
        }

        // Create new entry with MutableSharedFlow
        val sharedFlow = MutableSharedFlow<WordResult>(replay = 1)
        val entry = FetchEntry(sharedFlow)
        activeFetches[key] = entry

        // Start collecting from DictionaryClient and forwarding to SharedFlow
        scope.launch {
            dictionaryClient.getWord(language, normalizedLemma, normalizedTargets, pushToRepo)
                .onCompletion {
                    // Mark as complete - will be removed on next getWord call
                    entry.isComplete = true
                }
                .collect { result ->
                    sharedFlow.emit(result)
                }
        }

        sharedFlow
    }
}

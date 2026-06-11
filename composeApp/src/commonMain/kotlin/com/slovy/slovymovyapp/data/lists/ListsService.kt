package com.slovy.slovymovyapp.data.lists

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.ListsClient
import com.slovy.slovymovyapp.data.remote.RemoteList
import com.slovy.slovymovyapp.logging.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serves curated word lists from the app DB and keeps the stored copy in sync with the
 * server. Reads never touch the network; [sync] compares the stored bundle version with
 * `/lists/{lang}/version` and refetches only on mismatch, so screens can call it freely.
 */
class ListsService(
    private val repository: WordListsRepository,
    private val client: ListsClient,
) {
    private val syncMutex = Mutex()

    suspend fun getLists(language: Language): List<WordList> = repository.getLists(language)

    suspend fun getList(language: Language, listId: String): WordList? =
        repository.getList(language, listId)

    /**
     * Brings the stored lists for [language] up to date with the server. Returns true
     * when new content was written. Network failures and bundles rejected by the DB
     * (e.g. a duplicate sense id within a list) leave the stored rows untouched.
     */
    suspend fun sync(language: Language): Boolean = syncMutex.withLock {
        val remoteVersion = client.getListsVersion(language) ?: return false
        if (remoteVersion == repository.getVersion(language)) return false
        val response = client.getLists(language) ?: return false
        try {
            // Persist in feed order: ascending `order`, with absent (null) sorted to the
            // end and id as the alphabetical tiebreaker. `replaceLists` encodes this into
            // each row's `position`, so the feed and detail screens read it back directly.
            val ordered = response.lists.sortedWith(
                compareBy({ it.order ?: Int.MAX_VALUE }, { it.id }),
            )
            repository.replaceLists(
                language = language,
                version = response.version,
                lists = ordered.map { it.toWordList() },
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Storing lists for ${language.code} failed", e)
            false
        }
    }

    /**
     * Developer tooling: drops every stored list bundle and version (all languages), so
     * the next [sync] does a full refetch. Returns the number of lists removed. Takes the
     * sync mutex so it cannot interleave with a concurrent [sync].
     */
    suspend fun clearCache(): Long = syncMutex.withLock { repository.clearAll() }

    private fun RemoteList.toWordList() = WordList(
        id = id,
        title = title,
        subtitle = subtitle,
        labels = labels,
        senseIds = senseIds,
        iconSvg = iconSvg,
    )

    private companion object {
        const val TAG = "ListsService"
    }
}

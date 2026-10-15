package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Edits one language's study formats on behalf of a screen: the in-memory selection flips at
 * once, the store follows in tap order, and a failed store reverts to what is stored.
 *
 * Both the session page and the Settings page edit through this, so the ordering and recovery
 * rules live once. [onSelectionChanged] runs on every change of the selection the screen should
 * show, [onStored] after a toggle has landed in the store.
 */
class StudyFormatsEditor(
    private val repository: StudyFormatsRepository,
    private val language: Language,
    private val scope: CoroutineScope,
    private val analyticsSource: String,
    private val onSelectionChanged: (StudyFormatSelection) -> Unit,
    private val onStored: suspend () -> Unit,
) {
    /** Null until [load] has run. */
    var selection: StudyFormatSelection? = null
        private set

    private var write: Job? = null

    suspend fun load(): StudyFormatSelection = remember(repository.get(language))

    /** As [load], for a caller that has the language's translation targets in hand. */
    suspend fun load(translationTargets: List<Language>): StudyFormatSelection =
        remember(repository.get(language, translationTargets))

    private fun remember(loaded: StudyFormatSelection): StudyFormatSelection {
        selection = loaded
        return loaded
    }

    fun toggle(format: StudyFormat) {
        val current = selection ?: return
        val next = current.with(format, enabled = format !in current.enabled) ?: return
        selection = next
        onSelectionChanged(next)
        val previousWrite = write
        write = scope.launch {
            // Writes land in tap order.
            previousWrite?.join()
            try {
                repository.set(language, next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to store study formats lang=${language.code}", e)
                revertAfterFailedWrite(fallback = current)
                return@launch
            }
            Analytics.logEvent(
                AnalyticsEvent.STUDY_FORMAT_TOGGLED,
                mapOf(
                    "lang" to language.code,
                    "format" to format.name.lowercase(),
                    "enabled" to (format in next.enabled),
                    "source" to analyticsSource,
                ),
            )
            onStored()
        }
    }

    /**
     * Waits for the latest toggle to be stored. Work that reads the stored selection, or that
     * must come after it, calls this first so the two never disagree.
     */
    suspend fun awaitWrite() {
        write?.join()
    }

    private suspend fun revertAfterFailedWrite(fallback: StudyFormatSelection) {
        // Only the latest tap reverts. A tap queued after a failed one stores its own value or
        // reverts itself; reverting here as well would leave the screen behind that later value.
        if (write !== coroutineContext[Job]) return
        val stored = try {
            repository.get(language)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Even the read failed: the tap is simply undone.
            fallback
        }
        selection = stored
        onSelectionChanged(stored)
    }

    private companion object {
        const val TAG = "StudyFormatsEditor"
    }
}

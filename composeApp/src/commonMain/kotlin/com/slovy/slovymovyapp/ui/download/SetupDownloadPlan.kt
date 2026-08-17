package com.slovy.slovymovyapp.ui.download

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.CancelToken
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DownloadProgress
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * The set of database files one `DownloadSetup` run has to fetch, plus the work that fetches them.
 *
 * Both entry points into that screen download the same kinds of files — an optional dictionary and
 * any missing `source -> target` translation DBs — and share one progress bar split evenly across
 * the items. They differ only in which learning languages and translation targets take part:
 * initial setup fetches the chosen dictionary with its targets, while adding a translation language
 * from Settings fetches that one target for every already installed dictionary.
 *
 * [loadItems] resolves the plan against what is installed locally and what the server offers, and
 * [download] then fetches exactly what [loadItems] resolved, so the two must be called in that
 * order (which is what `DownloadViewModel` does).
 */
class SetupDownloadPlan(
    private val dataDbManager: DataDbManager,
    private val learningLanguages: suspend () -> List<Language>,
    private val translationTargets: List<Language>,
    /** Dictionary to fetch when it is not installed yet; `null` never downloads a dictionary. */
    private val dictionaryLanguage: Language?,
    /** Localized label for the dictionary item; unused when [dictionaryLanguage] is `null`. */
    private val dictionaryItemLabel: String,
) {
    private var downloadDictionary = false
    private val translationDownloads = mutableListOf<TranslationDownload>()

    suspend fun loadItems(): List<DownloadItem> {
        val available = dataDbManager.fetchAvailableLanguages()
        val items = mutableListOf<DownloadItem>()

        val dictionary = dictionaryLanguage
        downloadDictionary = dictionary != null && !dataDbManager.hasDictionary(dictionary)
        if (downloadDictionary && dictionary != null) {
            available.find { it.language == dictionary }?.dictionarySizeBytes?.let { size ->
                items.add(DownloadItem(dictionaryItemLabel, size, dictionary.flag))
            }
        }

        translationDownloads.clear()
        learningLanguages().forEach { source ->
            downloadableTargets(source)
                .filter { !dataDbManager.hasTranslation(source, it) }
                .forEach { target ->
                    translationDownloads.add(TranslationDownload(source, target))
                    available.find { it.language == source }
                        ?.availableTranslations?.find { it.targetLanguage == target }?.sizeBytes
                        ?.let { size ->
                            items.add(DownloadItem(translationItemLabel(source, target), size, target.flag))
                        }
                }
        }

        return items
    }

    suspend fun download(onProgress: (DownloadProgress) -> Unit, cancelToken: CancelToken) {
        val totalItems = (if (downloadDictionary) 1 else 0) + translationDownloads.size
        if (totalItems == 0) return

        val dictionary = dictionaryLanguage
        if (downloadDictionary && dictionary != null) {
            dataDbManager.ensureDictionary(
                lang = dictionary,
                onProgress = { progress ->
                    onProgress(scaled(progress, itemIndex = 0, totalItems = totalItems, label = dictionaryItemLabel))
                },
                cancelToken = cancelToken,
            )
        }
        val translationOffset = if (downloadDictionary) 1 else 0
        translationDownloads.forEachIndexed { index, item ->
            val itemIndex = index + translationOffset
            val label = translationItemLabel(item.source, item.target)
            dataDbManager.ensureTranslation(
                src = item.source,
                tgt = item.target,
                onProgress = { progress ->
                    onProgress(scaled(progress, itemIndex = itemIndex, totalItems = totalItems, label = label))
                },
                cancelToken = cancelToken,
            )
        }
    }

    /** Remaps one file's progress onto the run-wide bar: whole items done plus this file's share. */
    private fun scaled(
        progress: DownloadProgress,
        itemIndex: Int,
        totalItems: Int,
        label: String,
    ): DownloadProgress {
        val base = (itemIndex.toFloat() / totalItems) * 100
        val current = if (progress.percent >= 0) progress.percent.toFloat() / totalItems else 0f
        return object : DownloadProgress(progress.bytesDownloaded, progress.totalBytes) {
            override val percent: Int = (base + current).toInt()
            override val currentFile: String = label
        }
    }

    /**
     * Availability lookups are best-effort: a target the server cannot serve simply drops out of
     * the plan, and its content is filled in later by online fetches plus lemma recovery.
     */
    private suspend fun downloadableTargets(source: Language): List<Language> = try {
        dataDbManager.downloadableTranslationTargets(source, translationTargets)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.warn(TAG, "Unable to load downloadable translations for ${source.code}", e)
        emptyList()
    }

    private fun translationItemLabel(source: Language, target: Language): String =
        "${source.selfName} → ${target.selfName}"

    private data class TranslationDownload(
        val source: Language,
        val target: Language,
    )

    private companion object {
        const val TAG = "SetupDownloadPlan"
    }
}

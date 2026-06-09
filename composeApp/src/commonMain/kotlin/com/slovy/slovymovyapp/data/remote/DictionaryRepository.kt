package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.forms.*
import com.slovy.slovymovyapp.data.forms.configs.SchemeRegistry
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.dictionary.*
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.translation.TranslationDatabase
import com.slovy.slovymovyapp.translation.TranslationQueries
import com.slovy.slovymovyapp.util.stripAccents
import kotlinx.coroutines.*
import kotlin.uuid.Uuid

internal fun DictionaryPos.toPartOfSpeech(): PartOfSpeech {
    return PartOfSpeech.valueOf(this.name)
}

private fun hasAnyResolvedForm(resolved: List<List<String?>>): Boolean =
    resolved.any { row -> row.any { it != null } }

fun resolveSchemeView(
    forms: List<SchemeInputForm>,
    view: SchemeView,
    tagResolver: SchemeTagResolver = DefaultSchemeTagResolver,
    lemma: String
): List<List<String?>> {

    data class ResolvedFormTags(
        val form: String,
        val tagsByKey: Map<String, Set<String>>,
        val mappedTagCount: Int,
        val source: FormSource,
    )

    val preprocessedForms = tagResolver.preprocessForms(forms = forms, lemma = lemma)

    val resolvedForms = preprocessedForms.map { form ->
        val mappedTags = TagMapping.resolve(form.tags)
        ResolvedFormTags(
            form = form.form,
            tagsByKey = mappedTags
                .groupBy { it.key }
                .mapValues { (_, tags) -> tags.map { it.value }.toSet() },
            mappedTagCount = mappedTags.size,
            source = form.source
        )
    }
    return view.grid.map { row ->
        row.map { cell ->
            when (cell) {
                is GridCell.Data -> resolvedForms
                    .asSequence()
                    .mapNotNull { resolvedForm ->
                        val matchedRequiredTags = cell.requiredTags.count { (key, expectedValue) ->
                            resolvedForm.tagsByKey[key]?.contains(expectedValue) == true
                        }
                        if (matchedRequiredTags != cell.requiredTags.size) return@mapNotNull null

                        // Forbidden tags: exclude forms that carry any of the forbidden tag values.
                        if (cell.forbiddenTags.any { (key, forbiddenValues) ->
                                resolvedForm.tagsByKey[key]?.any { it in forbiddenValues } == true
                            }) return@mapNotNull null

                        // Source filter: skip this form if it doesn't match the cell's allowed sources.
                        // A null form source fails the filter when sources are restricted.
                        val allowedSources = cell.allowedSources
                        if (allowedSources != null && resolvedForm.source !in allowedSources) {
                            return@mapNotNull null
                        }

                        val matchedPreferredTags = cell.preferredTags.count { (key, supportingValue) ->
                            resolvedForm.tagsByKey[key]?.contains(supportingValue) == true
                        }
                        val extraKnownTags = resolvedForm.mappedTagCount - matchedRequiredTags
                        SchemeCellCandidate(
                            matchedPreferredTags = matchedPreferredTags,
                            extraKnownTags = extraKnownTags,
                            form = resolvedForm.form
                        )
                    }
                    .toList()
                    .let(tagResolver::selectCandidate)
                    ?.form

                else -> null
            }
        }
    }
}

private fun resolveNonEmptyFormsViews(
    language: Language,
    pos: DictionaryPos,
    forms: List<SchemeInputForm>,
    lemma: String
): List<FormsSchemeView> {
    val selectedScheme = SchemeRegistry.findScheme(language, pos, forms) ?: return emptyList()
    return selectedScheme.views.mapNotNull { view ->
        val resolved = resolveSchemeView(
            forms = forms,
            view = view,
            tagResolver = selectedScheme.tagResolver,
            lemma = lemma
        )

        if (!hasAnyResolvedForm(resolved)) return@mapNotNull null

        FormsSchemeView(
            view = view.copy(viewId = "${selectedScheme.templateId}:${view.viewId}"),
            forms = resolved
        )
    }
}

// Repository that provides search across installed dictionaries and builds LanguageCard by lemma ID,
// aggregating translations from all available target languages.
class DictionaryRepository(
    private val dataDbManager: DataDbManager,
    private val localDbManager: LocalDbManager,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
    private val languages: List<Language> = Language.entries,
) {

    data class SearchItem(
        val language: Language,
        val lemmaId: Uuid, // Base lemma ID (not lemma_pos ID)
        val lemma: String,
        val display: String,
        val zipfFrequency: Float,
        val pos: List<PartOfSpeech>,
        val isFavorite: Boolean = false,
        val onlineOnly: Boolean,
    )

    data class SenseWithPos(
        val sense: LanguageCardResponseSense,
        val pos: PartOfSpeech,
        val relatedWords: Map<String, RelatedWord> = emptyMap()
    )

    data class SenseLookupResult(
        val sense: SenseWithPos? = null,
        val missingReason: FavoriteSenseMissingReason? = null,
    )

    enum class FavoriteSenseMissingReason {
        DICTIONARY_NOT_DOWNLOADED,
        MEANING_NOT_FOUND,
        ONLINE_ONLY,
    }

    data class ListSenseItem(
        val senseId: String,
        val lemma: String,
        val definition: String,
        val learnerLevel: LearnerLevel,
        val frequency: SenseFrequency,
    )

    // Cache for loaded senses - reusable across the app
    private val senseCache = linkedMapOf<String, SenseWithPos>()

    companion object {
        private const val TAG = "DictionaryRepository"
        private const val SENSE_CACHE_MAX_SIZE = 500
    }

    /**
     * Adds sense to cache, evicting oldest entries if over limit.
     */
    private fun cacheSense(senseId: String, value: SenseWithPos) {
        senseCache[senseId] = value
        // Evict oldest entries if over limit
        while (senseCache.size > SENSE_CACHE_MAX_SIZE) {
            val oldest = senseCache.keys.firstOrNull() ?: break
            senseCache.remove(oldest)
        }
    }

    /**
     * Gets a cached sense if available.
     */
    fun getCachedSense(senseId: String): SenseWithPos? = senseCache[senseId]

    /**
     * Removes cached senses to force reloading updated data (e.g., new translations).
     */
    fun invalidateSenses(senseIds: Set<String>) {
        if (senseIds.isEmpty()) return
        senseIds.forEach { senseCache.remove(it) }
    }

    /**
     * Gets all cached senses for the given IDs.
     */
    fun getCachedSenses(senseIds: Set<String>): Map<String, SenseWithPos> =
        senseIds.mapNotNull { id -> senseCache[id]?.let { id to it } }.toMap()

    /**
     * Clears all cached senses. Call when dictionaries or translations are
     * added/removed to ensure stale data isn't served.
     */
    fun clearSenseCache() {
        senseCache.clear()
    }

    /**
     * Runs [block] with the dictionary databases for [language] in priority order (downloaded RO
     * first, then local). Both the downloaded RO database AND the local writable database are
     * held under read leases so a concurrent delete on either source cannot tear it down while
     * [block] is in flight.
     *
     * The local DB existence check happens **inside** the local read lock
     * ([LocalDbManager.withLocalDictionaryIfExists]) — no TOCTOU.
     */
    private suspend fun <T> withDictionaryDatabases(
        language: Language,
        block: suspend (List<DictionaryDatabase>) -> T,
    ): T = localDbManager.withLocalDictionaryIfExists { localDb ->
        dataDbManager.withDictionaryReadOnlyIfExists(language) { downloadedDb ->
            block(listOfNotNull(downloadedDb, localDb))
        }
    }

    /**
     * Runs [block] with translation databases for ([src], [tgt]) in priority order. Both the
     * downloaded RO translation and the local writable translation are leased for the duration.
     */
    private suspend fun <T> withTranslationDatabases(
        src: Language,
        tgt: Language,
        block: suspend (List<TranslationDatabase>) -> T,
    ): T = localDbManager.withLocalTranslationIfExists { localDb ->
        dataDbManager.withTranslationReadOnlyIfExists(src, tgt) { downloadedDb ->
            block(listOfNotNull(downloadedDb, localDb))
        }
    }

    /**
     * Recursively acquires translation-database leases for each target language in [targets] and
     * invokes [block] with a map from each target language to its priority-ordered list of
     * databases. All leases stay held for the entire [block].
     */
    private suspend fun <T> withTranslationDatabasesForTargets(
        src: Language,
        targets: List<Language>,
        block: suspend (Map<Language, List<TranslationDatabase>>) -> T,
    ): T {
        suspend fun acquireRemaining(
            remaining: List<Language>,
            acquired: Map<Language, List<TranslationDatabase>>,
        ): T {
            if (remaining.isEmpty()) return block(acquired)
            val tgt = remaining.first()
            return withTranslationDatabases(src, tgt) { dbs ->
                acquireRemaining(remaining.drop(1), acquired + (tgt to dbs))
            }
        }
        return acquireRemaining(targets, emptyMap())
    }

    // Loads related words from all databases. Offline rows replace online-only rows.
    // Direct lemma hits always beat form-fallback results, even offline ones from an earlier DB.
    private fun loadRelatedWords(
        databases: List<DictionaryDatabase>,
        language: Language,
        relatedWords: Set<String>
    ): Map<String, RelatedWord> {
        if (relatedWords.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, RelatedWord>()
        // Normalize to lowercase for case-insensitive matching (DB stores lemmas lowercase)
        val lookupWords = relatedWords.map { it.lowercase() }.toSet()
        // Forms confirmed as standalone lemmas — the fallback must never redirect these to a parent.
        val foundAsLemmaKeys = mutableSetOf<String>()
        // Keys whose current result came from a form-fallback; a direct lemma hit may still override them.
        val formFallbackKeys = mutableSetOf<String>()

        fun putFromLemma(key: String, relatedWord: RelatedWord) {
            // A direct lemma hit wins over: (1) any online-only result, (2) any form-fallback result.
            if (result[key]?.online != false || key in formFallbackKeys) {
                result[key] = relatedWord
                formFallbackKeys.remove(key)
            }
        }

        fun putFromFallback(key: String, relatedWord: RelatedWord) {
            if (result[key]?.online != false) {
                result[key] = relatedWord
                formFallbackKeys.add(key)
            }
        }

        for (db in databases) {
            try {
                val q = db.dictionaryQueries
                q.selectLemmasByWords(language.code, lookupWords.toList())
                    .executeAsList()
                    .forEach { row ->
                        putFromLemma(row.lemma, relatedWord(row.lemma, row.zipf_frequency, row.online_only))
                        foundAsLemmaKeys.add(row.lemma)
                    }

                // Fallback: resolve inflected forms (e.g. "Gebogen" → parent lemma "buigen").
                // Only applies to forms that are not standalone lemmas; standalone lemmas navigate
                // to themselves regardless of online/offline status.
                // Keep the form as the map key so chip text stays unchanged, while
                // RelatedWord.lemma points navigation at the parent lemma.
                lookupWords
                    .filter { form -> form !in foundAsLemmaKeys }
                    .forEach { form ->
                        q.resolveRelatedForm(language, form)?.let { relatedWord ->
                            putFromFallback(form, relatedWord)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Related words are decorative — a per-DB failure must not abort the surrounding
                // card load. Log and continue with whatever the remaining DBs supply.
                AppLogger.warn(TAG, "loadRelatedWords failed for ${language.code}", e)
            }
        }
        return result
    }

    private fun DictionaryQueries.resolveRelatedForm(
        language: Language,
        form: String
    ): RelatedWord? {
        val exact = selectLemmasByFormEquals(language.code, form, 1L)
            .executeAsList()
            .firstOrNull()
        if (exact != null) {
            return relatedWord(exact.lemma, exact.zipf_frequency, exact.online_only)
        }

        val normalized = selectLemmasByFormNormalizedEquals(language.code, stripAccents(form), 1L)
            .executeAsList()
            .firstOrNull()
            ?: return null

        return relatedWord(normalized.lemma, normalized.zipf_frequency, normalized.online_only)
    }

    private fun relatedWord(
        lemma: String,
        zipfFrequency: Number,
        onlineOnly: Boolean
    ): RelatedWord = RelatedWord(
        lemma = lemma,
        zipfFrequency = zipfFrequency.toFloat(),
        online = onlineOnly
    )

    fun installedDictionaries(): List<Language> = languages.filter { lang ->
        try {
            dataDbManager.hasDictionary(lang)
        } catch (_: Throwable) {
            false
        }
    }

    fun installedTranslationTargets(src: Language): List<Language> = languages.filter { tgt ->
        tgt != src && dataDbManager.hasTranslation(src, tgt)
    }

    suspend fun defaultTranslationTargets(src: Language): List<Language> {
        val configuredTargets = settingsRepository
            .getTranslationLanguagesOrNull()
            ?: return installedTranslationTargets(src)

        return configuredTargets
            .filter { it != src }
            .distinctBy { it.code }
    }

    // Search within all installed dictionaries by default; if dictionaryLanguage provided, restrict to it.
    // translationTargets: if null, uses defaultTranslationTargets for each source language; if empty, skips translation search.
    suspend fun search(
        query: String,
        dictionaryLanguage: Language? = null,
        translationTargets: List<Language>? = null,
        maxItems: Int = 200
    ): List<SearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val normalizedPrefix = stripAccents(trimmed)
        val (prefixStart, prefixEnd) = prefixRange(normalizedPrefix)

        val languages = if (dictionaryLanguage != null) listOf(dictionaryLanguage) else installedDictionaries()
        if (languages.isEmpty()) {
            // Fallback to simple in-memory filtering for preview/no DB
            return listOf()
        }

        val out = mutableListOf<SearchItem>()
        // Track seen items by language + display string to avoid duplicates
        val seenDisplays = HashSet<String>()
        // Track lemmas that were added as base lemmas to suppress their forms
        val seenLemmas = HashSet<String>()

        // Set to true when a per-language phase has hit `maxItems` and we want to short-circuit the
        // remaining work. Replaces the previous non-local `return finalizeSearchResults(...)`
        // pattern, which we can no longer use because the search body now runs inside non-inline
        // suspending lease blocks.
        var done = false

        for (lang in languages) {
            if (done) break
            // search by translation (target language words)
            val targets = translationTargets
                ?.filter { it != lang && dataDbManager.hasTranslation(lang, it) }
                ?: defaultTranslationTargets(lang)

            suspend fun runPerLanguage(downloadedDict: DictionaryDatabase?, localDict: DictionaryDatabase?) {
                // Build database list: local first, then RO. Preserves the original priority where
                // local results win over downloaded for the same lemma.
                val databases = buildList {
                    if (localDict != null) {
                        add(localDict)
                    }
                    if (downloadedDict != null) {
                        add(downloadedDict)
                    }
                }

                fun addLemma(lemmaId: Uuid, lemma: String, zipfFrequency: Float, onlineOnly: Boolean) {
                    val key = "${lang.code}::$lemma"
                    if (!seenDisplays.contains(key)) {
                        out.add(
                            SearchItem(
                                language = lang,
                                lemmaId = lemmaId,
                                lemma = lemma,
                                display = lemma,
                                zipfFrequency = zipfFrequency,
                                pos = emptyList(),
                                onlineOnly = onlineOnly
                            )
                        )
                        seenDisplays.add(key)
                        seenLemmas.add("${lang.code}::${lemma.lowercase()}")
                    }
                }

                fun addForm(
                    lemmaId: Uuid,
                    lemma: String,
                    form: String,
                    zipfFrequency: Float,
                    onlineOnly: Boolean
                ) {
                    val lemmaKey = "${lang.code}::${lemma.lowercase()}"
                    if (seenLemmas.contains(lemmaKey)) {
                        return
                    }

                    val display = "\"$form\" form of \"$lemma\""
                    val key = "${lang.code}::$display"
                    if (!seenDisplays.contains(key)) {
                        out.add(
                            SearchItem(
                                language = lang,
                                lemmaId = lemmaId,
                                lemma = lemma,
                                display = display,
                                zipfFrequency = zipfFrequency,
                                pos = emptyList(),
                                onlineOnly = onlineOnly
                            )
                        )
                        seenDisplays.add(key)
                    }
                }

                fun addTranslation(
                    lemmaId: Uuid,
                    lemma: String,
                    translation: String,
                    zipfFrequency: Float,
                    onlineOnly: Boolean
                ) {
                    val lemmaKey = "${lang.code}::${lemma.lowercase()}"
                    if (seenLemmas.contains(lemmaKey)) {
                        return
                    }

                    val display = "\"$translation\" translation of \"$lemma\""
                    val key = "${lang.code}::$display"
                    if (!seenDisplays.contains(key)) {
                        out.add(
                            SearchItem(
                                language = lang,
                                lemmaId = lemmaId,
                                lemma = lemma,
                                display = display,
                                zipfFrequency = zipfFrequency,
                                pos = emptyList(),
                                onlineOnly = onlineOnly
                            )
                        )
                        seenDisplays.add(key)
                    }
                }

                fun enrichPosForLang(q: DictionaryQueries) {
                    val lemmaIds = out.filter { it.language == lang && it.pos.isEmpty() }
                        .map { it.lemmaId }.toSet().toList()
                    if (lemmaIds.isNotEmpty()) {
                        val posResults = q.selectLemmaIdAndPosByLemmaIds(lemmaIds).executeAsList()
                        val lemmaIdToPosMap = posResults.groupBy({ it.id }, { it.pos.toPartOfSpeech() })
                        for (i in out.indices) {
                            if (out[i].language == lang && out[i].pos.isEmpty()) {
                                val posList = lemmaIdToPosMap[out[i].lemmaId] ?: emptyList()
                                if (posList.isNotEmpty()) {
                                    out[i] = out[i].copy(pos = posList)
                                }
                            }
                        }
                    }
                }

                fun shouldEarlyReturn(q: DictionaryQueries): Boolean {
                    if (out.size >= maxItems) {
                        enrichPosForLang(q)
                        return true
                    }
                    return false
                }

                // Search each database (local first, then RO)
                // 1) Exact lemma matches across all databases
                for (db in databases) {
                    val q = db.dictionaryQueries
                    val byWord: List<SelectLemmasByWord> =
                        q.selectLemmasByWord(lang.code, trimmed).executeAsList()
                    val byNorm: List<SelectLemmasByNormalized> =
                        q.selectLemmasByNormalized(lang.code, trimmed).executeAsList()
                    byWord.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
                    byNorm.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
                    if (shouldEarlyReturn(q)) {
                        done = true
                        return
                    }
                    currentCoroutineContext().ensureActive()
                }

                // 2) Exact form matches across all databases
                for (db in databases) {
                    val q = db.dictionaryQueries
                    val formEq: List<SelectLemmasByFormEquals> =
                        q.selectLemmasByFormEquals(lang.code, trimmed, maxItems.toLong()).executeAsList()
                    val formEqNorm: List<SelectLemmasByFormNormalizedEquals> =
                        q.selectLemmasByFormNormalizedEquals(lang.code, trimmed, maxItems.toLong()).executeAsList()
                    formEq.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
                    formEqNorm.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
                    if (shouldEarlyReturn(q)) {
                        done = true
                        return
                    }
                    currentCoroutineContext().ensureActive()
                }

                // 3) Prefix lemma matches across all databases
                for (db in databases) {
                    val q = db.dictionaryQueries
                    val lemmaNormLike: List<SelectLemmasNormalizedLike> =
                        q.selectLemmasNormalizedLike(lang.code, prefixStart, prefixEnd, maxItems.toLong()).executeAsList()
                    lemmaNormLike.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
                    if (shouldEarlyReturn(q)) {
                        done = true
                        return
                    }
                    currentCoroutineContext().ensureActive()
                }

                // 4) Prefix form matches across all databases
                for (db in databases) {
                    val q = db.dictionaryQueries
                    val formNormLike: List<SelectLemmasFromFormsNormalizedLike> =
                        q.selectLemmasFromFormsNormalizedLike(lang.code, prefixStart, prefixEnd, maxItems.toLong())
                            .executeAsList()
                    formNormLike.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
                    if (shouldEarlyReturn(q)) {
                        done = true
                        return
                    }
                    enrichPosForLang(q)
                    currentCoroutineContext().ensureActive()
                }

                // Translation search: local first, then RO
                for (tgt in targets) {
                    if (done) return

                    // Closure that processes one (translation, dictionary) pair. Returns true when
                    // we hit maxItems and the outer logic should stop.
                    fun processPair(tdb: TranslationDatabase, dictDb: DictionaryDatabase): Boolean {
                        val tq = tdb.translationQueries
                        val dq = dictDb.dictionaryQueries
                        val trRows =
                            tq.selectSenseTranslationsByNormalizedPrefix(lang.code, tgt.code, prefixStart, prefixEnd)
                                .executeAsList()
                        val lemmaRows =
                            dq.selectLemmasByIds(trRows.map { it.lemma_id }).executeAsList().associateBy { it.id }
                        val trRowsSorted = trRows.sortedByDescending { lemmaRows[it.lemma_id]?.zipf_frequency }
                        for (row in trRowsSorted) {
                            val lemmaRow = lemmaRows[row.lemma_id]
                            if (lemmaRow != null) {
                                addTranslation(
                                    lemmaRow.id,
                                    lemmaRow.lemma,
                                    row.target_lang_word,
                                    lemmaRow.zipf_frequency.toFloat(),
                                    lemmaRow.online_only
                                )
                                if (shouldEarlyReturn(dq)) return true
                            }
                        }
                        enrichPosForLang(dq)
                        return false
                    }

                    // Local pair first. Both local DBs are held under their own read leases so a
                    // concurrent LocalDbManager.deleteAll() cannot tear them down mid-read. The
                    // existence checks happen inside the leases (no TOCTOU).
                    var hitMax = false
                    localDbManager.withLocalTranslationIfExists { localTrans ->
                        if (localTrans != null) {
                            localDbManager.withLocalDictionaryIfExists { localDict ->
                                if (localDict != null) {
                                    hitMax = processPair(localTrans, localDict)
                                }
                            }
                        }
                    }
                    if (hitMax) {
                        done = true
                        return
                    }

                    // Downloaded translation pair. The dictionary lease is already held by the
                    // outer withDictionaryReadOnlyIfExists; the translation lease is acquired
                    // here. Existence check happens inside the lease, so a concurrent delete
                    // can't race with us.
                    if (downloadedDict != null) {
                        var hitMax = false
                        dataDbManager.withTranslationReadOnlyIfExists(lang, tgt) { downloadedTrans ->
                            if (downloadedTrans != null) {
                                hitMax = processPair(downloadedTrans, downloadedDict)
                            }
                        }
                        if (hitMax) {
                            done = true
                            return
                        }
                    }
                }
            }

            // Both DBs are held under their respective read leases; existence checks happen
            // inside the IfExists variants so there is no TOCTOU window.
            localDbManager.withLocalDictionaryIfExists { localDict ->
                dataDbManager.withDictionaryReadOnlyIfExists(lang) { downloaded ->
                    runPerLanguage(downloaded, localDict)
                }
            }

            // Check for cancellation before next language
            currentCoroutineContext().ensureActive()
        }

        return finalizeSearchResults(out, maxItems)
    }

    suspend fun getLanguageCard(
        language: Language,
        lemma: String,
        translationTargets: List<Language>? = null,
        senseIds: Set<String>? = null
    ): LanguageCard? = withContext(Dispatchers.IO) {
        val resolvedTranslationTargets = translationTargets ?: defaultTranslationTargets(language)
        try {
            withDictionaryDatabases(language) { dictDatabases ->
                withTranslationDatabasesForTargets(language, resolvedTranslationTargets) { translationDbsMap ->
                    computeLanguageCard(
                        language = language,
                        lemma = lemma,
                        senseIds = senseIds,
                        dictDatabases = dictDatabases,
                        translationDbsMap = translationDbsMap,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val info = dataDbManager.dictionaryDiagnostics(language)
            AppLogger.error(
                TAG,
                "getLanguageCard failed for lemma='$lemma' lang=${language.code} $info",
                e,
            )
            null
        }
    }

    private fun computeLanguageCard(
        language: Language,
        lemma: String,
        senseIds: Set<String>?,
        dictDatabases: List<DictionaryDatabase>,
        translationDbsMap: Map<Language, List<TranslationDatabase>>,
    ): LanguageCard? {
        val senseIdFilter = senseIds?.takeIf { it.isNotEmpty() }

        // Lookup lemma, trying databases in order
        var lemmaId: Uuid? = null
        var onlineOnly = false
        var zipfFrequency = 0f
        var sourceDb: DictionaryDatabase? = null

        // Normalize to lowercase for case-insensitive matching (DB stores lemmas lowercase)
        val normalizedLemma = lemma.lowercase()
        for (db in dictDatabases) {
            val result = db.dictionaryQueries
                .selectLemmasByWord(language.code, normalizedLemma)
                .executeAsList()
                .firstOrNull()
            if (result != null) {
                lemmaId = result.id
                onlineOnly = result.online_only
                zipfFrequency = result.zipf_frequency.toFloat()
                sourceDb = db
                if (!onlineOnly) break
            }
        }

        if (lemmaId == null || sourceDb == null) return null

        val q = sourceDb.dictionaryQueries

        // Get all lemma_pos IDs for this lemma
        val lemmaPosIds = q.selectLemmaPosIdByLemmaId(lemmaId).executeAsList()

        // Data class for sense row data we need to carry forward
        data class SenseRowData(
            val senseId: Uuid,
            val senseDefinition: String,
            val learnerLevel: LearnerLevel,
            val frequency: SenseFrequency,
            val semanticGroupId: String,
            val nameType: NameType?
        )

        // Data class for form data we need
        data class FormData(val formId: Uuid, val form: String, val source: FormSource)

        // Per-POS collected data
        data class PosData(
            val pos: PartOfSpeech,
            val forms: List<FormData>,
            val senseRows: List<SenseRowData>,
            val cachedSenses: Map<String, LanguageCardResponseSense>
        )

        val posDataList = mutableListOf<PosData>()
        val allFormIds = mutableListOf<Uuid>()
        val allUncachedSenseIds = mutableListOf<Uuid>()

        for (lemmaPosId in lemmaPosIds) {
            val lemmaPosRow = q.selectLemmaPosFullById(lemmaPosId).executeAsList().firstOrNull() ?: continue
            zipfFrequency = lemmaPosRow.zipf_frequency.toFloat()
            val formsWithId = q.selectFormsWithIdByLemmaPosId(lemmaPosId).executeAsList()
            val forms = formsWithId.map { FormData(it.form_id, it.form, it.source) }
            allFormIds.addAll(forms.map { it.formId })

            val sensesRows = q.selectSensesByLemmaPosId(lemmaPosId).executeAsList()
            val filteredSenses = senseIdFilter?.let { filter ->
                sensesRows.filter { filter.contains(it.sense_id.toString()) }
            } ?: sensesRows

            // Skip clusters with no matching senses for processed words. Raw-only words
            // (onlineOnly=true) may have clusters without senses and should still be returned.
            if (!onlineOnly && filteredSenses.isEmpty()) continue

            val pos = PartOfSpeech.valueOf(lemmaPosRow.pos.name)

            // Separate cached from uncached senses, convert to our data class
            val cachedSenses = mutableMapOf<String, LanguageCardResponseSense>()
            val senseRows = filteredSenses.map { s ->
                val cached = getCachedSense(s.sense_id.toString())
                if (cached != null) {
                    cachedSenses[s.sense_id.toString()] = cached.sense
                } else {
                    allUncachedSenseIds.add(s.sense_id)
                }
                SenseRowData(
                    senseId = s.sense_id,
                    senseDefinition = s.sense_definition,
                    learnerLevel = LearnerLevel.valueOf(s.learner_level.name),
                    frequency = SenseFrequency.valueOf(s.frequency.name),
                    semanticGroupId = s.semantic_group_id,
                    nameType = s.name_type?.let { NameType.valueOf(it.name) }
                )
            }

            posDataList.add(PosData(pos, forms, senseRows, cachedSenses))
        }

        if (posDataList.isEmpty()) return null

        // Batch load form tags
        val formTagsMap: Map<Uuid, List<String>> = if (allFormIds.isNotEmpty()) {
            q.selectFormTagsByFormIds(allFormIds)
                .executeAsList()
                .groupBy({ it.form_id }, { it.tag })
        } else emptyMap()

        // Data class for example data
        data class ExampleData(val exampleId: Long, val text: String)

        // Batch load sense data for uncached senses
        val synonymsMap: Map<Uuid, List<String>>
        val antonymsMap: Map<Uuid, List<String>>
        val phrasesMap: Map<Uuid, List<String>>
        val traitsMap: Map<Uuid, List<LanguageCardTrait>>
        val examplesMap: Map<Uuid, List<ExampleData>>

        if (allUncachedSenseIds.isNotEmpty()) {
            synonymsMap = q.selectSenseSynonymsBySenseIds(allUncachedSenseIds)
                .executeAsList()
                .groupBy({ it.sense_id }, { it.synonym })

            antonymsMap = q.selectSenseAntonymsBySenseIds(allUncachedSenseIds)
                .executeAsList()
                .groupBy({ it.sense_id }, { it.antonym })

            phrasesMap = q.selectSenseCommonPhrasesBySenseIds(allUncachedSenseIds)
                .executeAsList()
                .groupBy({ it.sense_id }, { it.phrase })

            traitsMap = q.selectSenseTraitsBySenseIds(allUncachedSenseIds)
                .executeAsList()
                .groupBy({ it.sense_id }) { row ->
                    LanguageCardTrait(
                        traitType = TraitType.valueOf(row.trait_type.name),
                        comment = row.comment
                    )
                }

            examplesMap = q.selectSenseExamplesBySenseIds(allUncachedSenseIds)
                .executeAsList()
                .groupBy({ it.sense_id }) { ExampleData(it.example_id, it.text) }
        } else {
            synonymsMap = emptyMap()
            antonymsMap = emptyMap()
            phrasesMap = emptyMap()
            traitsMap = emptyMap()
            examplesMap = emptyMap()
        }

        // Batch load translation data per target language
        data class TranslationData(
            val definitions: Map<Uuid, String>,
            val translations: Map<Uuid, List<LanguageCardTranslation>>,
            val exampleTranslations: Map<Uuid, Map<Long, String>>
        )

        val translationDataMap: Map<Language, TranslationData> = if (allUncachedSenseIds.isNotEmpty()) {
            translationDbsMap.mapNotNull { (tgt, transDbs) ->
                val definitions = LinkedHashMap<Uuid, String>()
                val translations = LinkedHashMap<Uuid, List<LanguageCardTranslation>>()
                val exampleTranslations = LinkedHashMap<Uuid, MutableMap<Long, String>>()

                for (transDb in transDbs) {
                    val queries = transDb.translationQueries

                    queries
                        .selectDefinitionsBySenseIds(allUncachedSenseIds, language.code, tgt.code)
                        .executeAsList()
                        .forEach { row ->
                            if (row.sense_id !in definitions) {
                                definitions[row.sense_id] = row.definition
                            }
                        }

                    queries
                        .selectSenseTranslationsBySenseIds(allUncachedSenseIds, language.code, tgt.code)
                        .executeAsList()
                        .groupBy({ it.sense_id }) { row ->
                            LanguageCardTranslation(
                                targetLangWord = row.target_lang_word,
                                targetLangSenseClarification = row.target_lang_sense_clarification,
                                idx = row.idx
                            )
                        }
                        .forEach { (senseId, rows) ->
                            if (rows.isNotEmpty() && senseId !in translations) {
                                translations[senseId] = rows
                            }
                        }

                    queries
                        .selectExampleTranslationsBySenseIds(allUncachedSenseIds, language.code, tgt.code)
                        .executeAsList()
                        .forEach { row ->
                            val senseTranslations = exampleTranslations.getOrPut(row.sense_id) { LinkedHashMap() }
                            if (row.example_id !in senseTranslations) {
                                senseTranslations[row.example_id] = row.translation
                            }
                        }
                }

                if (definitions.isEmpty() && translations.isEmpty() && exampleTranslations.isEmpty()) {
                    null
                } else {
                    tgt to TranslationData(
                        definitions = definitions,
                        translations = translations,
                        exampleTranslations = exampleTranslations
                    )
                }
            }.toMap()
        } else emptyMap()

        // Build entries
        val entries = posDataList.map { posData ->
            val forms = posData.forms.map { formData ->
                SchemeInputForm(
                    tags = formTagsMap[formData.formId] ?: emptyList(),
                    form = formData.form,
                    source = formData.source
                )
            }

            val dictionaryPos = DictionaryPos.valueOf(posData.pos.name)
            val formsViews = resolveNonEmptyFormsViews(language, dictionaryPos, forms, lemma)

            val senses = posData.senseRows.map { s ->
                // Return cached sense if available
                posData.cachedSenses[s.senseId.toString()]?.let { return@map it }

                // Build from batch-loaded data
                val senseExamples = examplesMap[s.senseId] ?: emptyList()
                val senseExampleTranslations = senseExamples.associateBy(
                    { it.exampleId },
                    { mutableMapOf<Language, String>() }
                )

                // Populate example translations from batch data
                val tgtDefinitions = LinkedHashMap<Language, String>()
                val tgtTranslations = LinkedHashMap<Language, List<LanguageCardTranslation>>()
                for ((tgt, transData) in translationDataMap) {
                    transData.definitions[s.senseId]?.let { tgtDefinitions[tgt] = it }
                    transData.translations[s.senseId]?.takeIf { it.isNotEmpty() }?.let {
                        tgtTranslations[tgt] = it
                    }
                    transData.exampleTranslations[s.senseId]?.forEach { (exampleId, translation) ->
                        senseExampleTranslations[exampleId]?.put(tgt, translation)
                    }
                }

                val examples = senseExamples.map { ex ->
                    LanguageCardExample(ex.text, senseExampleTranslations[ex.exampleId] ?: emptyMap())
                }

                val result = LanguageCardResponseSense(
                    senseId = s.senseId.toString(),
                    senseDefinition = s.senseDefinition,
                    learnerLevel = s.learnerLevel,
                    frequency = s.frequency,
                    semanticGroupId = s.semanticGroupId,
                    nameType = s.nameType,
                    examples = examples,
                    synonyms = synonymsMap[s.senseId] ?: emptyList(),
                    antonyms = antonymsMap[s.senseId] ?: emptyList(),
                    commonPhrases = phrasesMap[s.senseId] ?: emptyList(),
                    traits = traitsMap[s.senseId] ?: emptyList(),
                    targetLangDefinitions = tgtDefinitions,
                    translations = tgtTranslations,
                )
                result
            }

            LanguageCardPosEntry(
                pos = posData.pos,
                formsViews = formsViews,
                senses = senses
            )
        }

        if (entries.isEmpty()) return null

        // Fetch word family from all databases (union)
        val wordFamily = q.selectWordFamilyByLemmaId(lemmaId).executeAsList().toSet()
        // Load related words from all databases (later databases take precedence)
        val relatedWordsMap = loadRelatedWords(
            dictDatabases, language,
            collectAllRelatedWords(entries, wordFamily, lemma)
        )

        // Keep related words alongside each cached sense so lightweight sense loads
        // (e.g. Favorites) can still render clickable related terms.
        entries.forEach { entry ->
            entry.senses.forEach { sense ->
                cacheSense(
                    sense.senseId,
                    SenseWithPos(
                        sense = sense,
                        pos = entry.pos,
                        relatedWords = relatedWordsMap
                    )
                )
            }
        }

        return LanguageCard(
            entries = entries,
            lemma = lemma,
            zipfFrequency = zipfFrequency,
            wordFamily = wordFamily.toList(),
            relatedWords = relatedWordsMap,
            online = onlineOnly,
        )
    }

    /**
     * Gets senses by IDs, using cache when available.
     * Results are automatically cached for future use. Requested IDs that cannot
     * be resolved are returned with a missing reason.
     */
    suspend fun getSenses(
        language: Language,
        lemma: String,
        senseIds: Set<String>,
        translationTargets: List<Language>? = null,
    ): Map<String, SenseLookupResult> {
        if (senseIds.isEmpty()) return emptyMap()

        // Check cache first
        val cached = getCachedSenses(senseIds)
        val results = LinkedHashMap<String, SenseLookupResult>()
        cached.forEach { (senseId, sense) ->
            results[senseId] = SenseLookupResult(sense = sense)
        }

        val missingIds = senseIds - cached.keys
        if (missingIds.isEmpty()) return results

        // Load missing senses (getLanguageCard will cache them automatically)
        val card = getLanguageCard(language, lemma, translationTargets, missingIds)
        card?.entries?.forEach { entry ->
            entry.senses.forEach { sense ->
                results[sense.senseId] = SenseLookupResult(
                    sense = SenseWithPos(
                        sense = sense,
                        pos = entry.pos,
                        relatedWords = card.relatedWords
                    )
                )
            }
        }

        val stillMissingIds = senseIds - results.keys
        if (stillMissingIds.isNotEmpty()) {
            val reason = resolveSenseLookupMissingReason(language, lemma, card)
            stillMissingIds.forEach { senseId ->
                results[senseId] = SenseLookupResult(missingReason = reason)
            }
        }

        return results
    }

    private suspend fun resolveSenseLookupMissingReason(
        language: Language,
        lemma: String,
        card: LanguageCard?,
    ): FavoriteSenseMissingReason = withContext(Dispatchers.IO) {
        when (card?.online) {
            true -> return@withContext FavoriteSenseMissingReason.ONLINE_ONLY
            false -> return@withContext FavoriteSenseMissingReason.MEANING_NOT_FOUND
            null -> Unit
        }

        withDictionaryDatabases(language) { dictDatabases ->
            if (dictDatabases.isEmpty()) {
                return@withDictionaryDatabases FavoriteSenseMissingReason.DICTIONARY_NOT_DOWNLOADED
            }

            val normalizedLemma = stripAccents(lemma.trim().lowercase())
            var foundLemma = false
            var foundOnlineOnly = false

            for (db in dictDatabases) {
                val rows = db.dictionaryQueries
                    .selectLemmasByNormalized(language.code, normalizedLemma)
                    .executeAsList()
                if (rows.isEmpty()) continue

                foundLemma = true
                if (rows.any { !it.online_only }) {
                    return@withDictionaryDatabases FavoriteSenseMissingReason.MEANING_NOT_FOUND
                }
                foundOnlineOnly = true
            }

            when {
                foundOnlineOnly -> FavoriteSenseMissingReason.ONLINE_ONLY
                !dataDbManager.hasDictionary(language) && !foundLemma -> FavoriteSenseMissingReason.DICTIONARY_NOT_DOWNLOADED
                else -> FavoriteSenseMissingReason.MEANING_NOT_FOUND
            }
        }
    }

    /**
     * Loads both word suggestions and recent favorite lemmas in a single pass,
     * fetching favorites from the DB only once.
     */
    suspend fun getSearchEmptyStateData(
        language: Language
    ): Pair<List<String>, List<String>> {
        val allFavorites = withContext(Dispatchers.IO) { favoritesRepository.getAll() }
        val suggestions = getWordSuggestions(language, favorites = allFavorites)
        val recentFavorites = getRecentFavoriteLemmas(language, favorites = allFavorites)
        return suggestions to recentFavorites
    }

    suspend fun getListSenses(language: Language, senseIds: List<String>): List<ListSenseItem> =
        withContext(Dispatchers.IO) {
            dataDbManager.withDictionaryReadOnlyIfExists(language) { db ->
                if (db == null) return@withDictionaryReadOnlyIfExists emptyList()
                val uuids = senseIds.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
                if (uuids.isEmpty()) return@withDictionaryReadOnlyIfExists emptyList()
                val byId = uuids.chunked(999).flatMap { chunk ->
                    db.dictionaryQueries.selectSensesForList(chunk).executeAsList()
                }.associateBy { it.sense_id.toString() }
                senseIds.mapNotNull { id ->
                    byId[id]?.let { row ->
                        ListSenseItem(
                            senseId = id,
                            lemma = row.lemma,
                            definition = row.sense_definition,
                            learnerLevel = LearnerLevel.valueOf(row.learner_level.name),
                            frequency = SenseFrequency.valueOf(row.frequency.name),
                        )
                    }
                }
            }
        }

    /**
     * Gets recent favorite lemmas for the given language, most recent first.
     * Accepts pre-fetched [favorites] to avoid redundant DB reads when the caller
     * already has the list (e.g. [getSearchEmptyStateData]).
     */
    suspend fun getRecentFavoriteLemmas(
        language: Language,
        limit: Int = 5,
        favorites: List<Favorite>? = null
    ): List<String> = withContext(Dispatchers.IO) {
        (favorites ?: favoritesRepository.getAll())
            .filter { it.language == language }
            .distinctBy { it.lemma.lowercase() }
            .take(limit)
            .map { it.lemma }
    }

    /**
     * Gets word suggestions for the empty search state.
     * Returns high-frequency words that are not in favorites.
     * Uses a random offset for variety.
     */
    suspend fun getWordSuggestions(
        language: Language,
        count: Int = 5,
        offset: Int = 2000,
        favorites: List<Favorite>? = null
    ): List<String> = withContext(Dispatchers.IO) {
        dataDbManager.withDictionaryReadOnlyIfExists(language) { db ->
            if (db == null) return@withDictionaryReadOnlyIfExists emptyList()
            val q = db.dictionaryQueries

            // Get favorites to exclude (case-insensitive)
            val favoriteLemmas = (favorites ?: favoritesRepository.getAll())
                .filter { it.language == language }
                .map { it.lemma.lowercase() }
                .toSet()

            val suggestions = mutableListOf<String>()
            val seenSuggestions = HashSet<String>()
            val batchSize = 100L
            val maxAttempts = 10

            repeat(maxAttempts) {
                if (suggestions.size >= count) return@repeat
                val randomOffset = (0..offset).random().toLong()

                val batch = q.selectTopFrequentLemmas(
                    language.code,
                    listOf(DictionaryPos.NAME),
                    batchSize,
                    randomOffset
                ).executeAsList()
                if (batch.isEmpty()) return@repeat

                // Sample non-favorite rows with gaps, then fill remaining from unused candidates.
                val candidates = batch.filter { it.lemma.lowercase() !in favoriteLemmas }
                if (candidates.isNotEmpty()) {
                    val startIndex = candidates.indices.random()
                    var index = startIndex
                    val step = (2..50).random()
                    while (suggestions.size < count && index < candidates.size) {
                        val lemma = candidates[index].lemma
                        val key = lemma.lowercase()
                        if (seenSuggestions.add(key)) {
                            suggestions.add(lemma)
                        }
                        index += step
                    }
                    if (suggestions.size < count) {
                        for (i in candidates.indices) {
                            if (suggestions.size >= count) break
                            val lemma = candidates[i].lemma
                            val key = lemma.lowercase()
                            if (seenSuggestions.add(key)) {
                                suggestions.add(lemma)
                            }
                        }
                    }
                }
            }

            suggestions.take(count)
        }
    }

    /**
     * Searches translation databases for sense_ids that have translations matching the query prefix.
     * Used for filtering favorites by translation text.
     *
     * @param senseIds The set of sense IDs to search within (e.g., favorite sense IDs)
     * @param query The search query (will be normalized and used as prefix)
     * @param sourceLanguage The source language of the dictionary
     * @return Set of sense IDs that have matching translations
     */
    suspend fun searchSenseIdsByTranslation(
        senseIds: Set<String>,
        query: String,
        sourceLanguage: Language
    ): Set<String> = withContext(Dispatchers.IO) {
        if (senseIds.isEmpty() || query.isBlank()) return@withContext emptySet()

        val normalizedQuery = stripAccents(query.trim().lowercase())
        val (prefixStart, prefixEnd) = prefixRange(normalizedQuery)

        // Convert string sense IDs to UUIDs for the query
        val senseUuids = senseIds.mapNotNull { id ->
            try {
                Uuid.parse(id)
            } catch (_: Exception) {
                null
            }
        }
        if (senseUuids.isEmpty()) return@withContext emptySet()

        val matchingSenseIds = mutableSetOf<String>()
        val installedTargets = installedTranslationTargets(sourceLanguage)

        // Search downloaded translation DBs for installed targets. Existence is checked inside
        // the IfExists lease so no TOCTOU.
        for (targetLang in installedTargets) {
            currentCoroutineContext().ensureActive()
            dataDbManager.withTranslationReadOnlyIfExists(sourceLanguage, targetLang) { transDb ->
                if (transDb != null) {
                    val results = searchSensesByTranslations(
                        transDb.translationQueries,
                        sourceLanguage,
                        prefixStart,
                        prefixEnd,
                        senseUuids,
                    )
                    matchingSenseIds.addAll(results.map { it.toString() })
                }
            }
        }

        currentCoroutineContext().ensureActive()

        // Search the local translation DB under a read lease so a concurrent deleteAll cannot
        // tear it down mid-read. The existence check happens inside the lease (no TOCTOU).
        localDbManager.withLocalTranslationIfExists { localTransDb ->
            if (localTransDb != null) {
                val results = searchSensesByTranslations(
                    localTransDb.translationQueries,
                    sourceLanguage,
                    prefixStart,
                    prefixEnd,
                    senseUuids,
                )
                matchingSenseIds.addAll(results.map { it.toString() })
            }
        }
        matchingSenseIds
    }

    private fun searchSensesByTranslations(
        translationQueries: TranslationQueries,
        sourceLanguage: Language,
        prefixStart: String,
        prefixEnd: String,
        senseUuids: List<Uuid>
    ): List<Uuid> {
        // sqlite limit for IN()
        return senseUuids.chunked(999).flatMap { chunk ->
            translationQueries
                .selectSenseIdsByTranslationWordPrefix(
                    sourceLanguage.code,
                    prefixStart,
                    prefixEnd,
                    chunk
                )
                .executeAsList()
        }
    }

    private fun prefixRange(prefix: String): Pair<String, String> {
        if (prefix.isEmpty()) return "" to "\uFFFF"
        return prefix to prefix + '\uFFFF'
    }

    private fun collectAllRelatedWords(
        entries: List<LanguageCardPosEntry>,
        wordFamily: Set<String>,
        lemma: String
    ): Set<String> {
        // Collect all unique related words (synonyms, antonyms, family, highlighted words)
        val allRelatedWords = mutableSetOf<String>()

        // Add synonyms and antonyms from all senses
        entries.forEach { entry ->
            entry.senses.forEach { sense ->
                allRelatedWords.addAll(sense.synonyms)
                allRelatedWords.addAll(sense.antonyms)

                // Extract highlighted words from sense definition
                allRelatedWords.addAll(HtmlTagParser.extractTaggedWords(sense.senseDefinition))

                // Extract highlighted words from examples
                sense.examples.forEach { example ->
                    allRelatedWords.addAll(HtmlTagParser.extractTaggedWords(example.text))
                }

                // Extract highlighted words from common phrases
                sense.commonPhrases.forEach { phrase ->
                    allRelatedWords.addAll(HtmlTagParser.extractTaggedWords(phrase))
                }
            }
        }

        // Add the word family
        allRelatedWords.addAll(wordFamily)

        // Remove the main lemma itself (case-insensitive)
        allRelatedWords.removeAll { it.equals(lemma, ignoreCase = true) }
        return allRelatedWords
    }

    private suspend fun finalizeSearchResults(
        out: List<SearchItem>,
        maxItems: Int
    ): List<SearchItem> {
        // Check for cancellation before final processing
        currentCoroutineContext().ensureActive()

        // Recheck online_only items against local database
        // Items marked online_only might exist in local DB (added after initial RO search)
        var result = out.take(maxItems)
        val onlineOnlyIds = result.filter { it.onlineOnly }.map { it.lemmaId }
        if (onlineOnlyIds.isNotEmpty()) {
            val localLemmaIds: Set<Uuid> = localDbManager.withLocalDictionaryIfExists { localDb ->
                localDb?.dictionaryQueries
                    ?.selectLemmasByIds(onlineOnlyIds)
                    ?.executeAsList()
                    ?.map { it.id }
                    ?.toSet() ?: emptySet()
            }

            result = result.map { item ->
                if (item.onlineOnly && item.lemmaId in localLemmaIds) {
                    item.copy(onlineOnly = false)
                } else {
                    item
                }
            }
        }

        // Check favorite status for all items (single query instead of N queries)
        val allFavorites = favoritesRepository.getAll()
        val favoriteItems = allFavorites.map { "${it.language.code}::${it.lemma.lowercase()}" }.toSet()

        return result.map { item ->
            val key = "${item.language.code}::${item.lemma.lowercase()}"
            if (favoriteItems.contains(key)) {
                item.copy(isFavorite = true)
            } else {
                item
            }
        }
    }
}

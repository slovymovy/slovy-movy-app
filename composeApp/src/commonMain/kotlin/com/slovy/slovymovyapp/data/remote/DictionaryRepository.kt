package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.dictionary.*
import com.slovy.slovymovyapp.translation.TranslationDatabase
import com.slovy.slovymovyapp.translation.TranslationQueries
import com.slovy.slovymovyapp.util.stripAccents
import kotlinx.coroutines.*
import kotlin.uuid.Uuid

internal fun DictionaryPos.toPartOfSpeech(): PartOfSpeech {
    return PartOfSpeech.valueOf(this.name)
}

// Repository that provides search across installed dictionaries and builds LanguageCard by lemma ID,
// aggregating translations from all available target languages.
class DictionaryRepository(
    private val dataDbManager: DataDbManager,
    private val localDbManager: LocalDbManager,
    private val favoritesRepository: FavoritesRepository,
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
        val pos: PartOfSpeech
    )

    // Cache for loaded senses - reusable across the app
    private val senseCache = linkedMapOf<String, SenseWithPos>()

    companion object {
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

    // Opens dictionary databases in priority order (RO first, then local)
    private suspend fun openDictionaryDatabases(language: Language): List<DictionaryDatabase> {
        return buildList {
            if (dataDbManager.hasDictionary(language)) {
                add(dataDbManager.openDictionaryReadOnly(language))
            }
            add(localDbManager.openLocalDictionary())
        }
    }

    // Opens translation databases in priority order (RO first, then local)
    private suspend fun openTranslationDatabases(src: Language, tgt: Language): List<TranslationDatabase> {
        return buildList {
            if (dataDbManager.hasTranslation(src, tgt)) {
                add(dataDbManager.openTranslationReadOnly(src, tgt))
            }
            add(localDbManager.openLocalTranslation())
        }
    }

    // Loads related words from all databases, later databases take precedence
    private fun loadRelatedWords(
        databases: List<DictionaryDatabase>,
        language: Language,
        relatedWords: Set<String>
    ): Map<String, RelatedWord> {
        if (relatedWords.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, RelatedWord>()
        // Normalize to lowercase for case-insensitive matching (DB stores lemmas lowercase)
        val lowercaseWords = relatedWords.map { it.lowercase() }.toSet()

        for (db in databases) {
            db.dictionaryQueries.selectLemmasByWords(language.code, lowercaseWords.toList())
                .executeAsList()
                .forEach { row ->
                    if (row.lemma !in result || result[row.lemma]!!.online) {
                        result[row.lemma] = RelatedWord(
                            lemma = row.lemma,
                            zipfFrequency = row.zipf_frequency.toFloat(),
                            online = row.online_only
                        )
                    }
                }
        }
        return result
    }

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

    // Search within all installed dictionaries by default; if dictionaryLanguage provided, restrict to it.
    // translationTargets: if null, uses installedTranslationTargets for each source language; if empty, skips translation search.
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

        for (lang in languages) {
            // search by translation (target language words)
            val targets = translationTargets
                ?.filter { it != lang && dataDbManager.hasTranslation(lang, it) }
                ?: installedTranslationTargets(lang)

            // Build database list: local first, then RO
            val databases = buildList {
                add(localDbManager.openLocalDictionary())
                if (dataDbManager.hasDictionary(lang)) {
                    add(dataDbManager.openDictionaryReadOnly(lang))
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
                // Skip forms if the base lemma is already in the results
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
                // Skip translation if the base lemma is already in the results
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

            // Enriches POS for items that don't have it yet (pos.isEmpty())
            fun enrichPosForLang(q: DictionaryQueries) {
                // Only enrich items without POS to avoid overwriting results from previous databases
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
            for (db in databases) {
                val q = db.dictionaryQueries

                // search exact lemma matches first
                val byWord: List<SelectLemmasByWord> =
                    q.selectLemmasByWord(lang.code, trimmed).executeAsList()
                val byNorm: List<SelectLemmasByNormalized> =
                    q.selectLemmasByNormalized(lang.code, trimmed).executeAsList()
                byWord.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
                byNorm.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
                if (shouldEarlyReturn(q)) return finalizeSearchResults(out, maxItems)

                // Check for cancellation before next stage
                currentCoroutineContext().ensureActive()

                // search exact form equals (including normalized)
                val formEq: List<SelectLemmasByFormEquals> =
                    q.selectLemmasByFormEquals(lang.code, trimmed, maxItems.toLong()).executeAsList()
                val formEqNorm: List<SelectLemmasByFormNormalizedEquals> =
                    q.selectLemmasByFormNormalizedEquals(lang.code, trimmed, maxItems.toLong()).executeAsList()
                formEq.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
                formEqNorm.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
                if (shouldEarlyReturn(q)) return finalizeSearchResults(out, maxItems)

                // Check for cancellation before next stage
                currentCoroutineContext().ensureActive()

                // and by prefix later (lemma and forms) - use normalized prefix range to stay index-friendly
                val lemmaNormLike: List<SelectLemmasNormalizedLike> =
                    q.selectLemmasNormalizedLike(lang.code, prefixStart, prefixEnd, maxItems.toLong()).executeAsList()
                lemmaNormLike.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
                if (shouldEarlyReturn(q)) return finalizeSearchResults(out, maxItems)

                // Check for cancellation before next stage
                currentCoroutineContext().ensureActive()

                val formNormLike: List<SelectLemmasFromFormsNormalizedLike> =
                    q.selectLemmasFromFormsNormalizedLike(lang.code, prefixStart, prefixEnd, maxItems.toLong())
                        .executeAsList()
                formNormLike.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
                if (shouldEarlyReturn(q)) return finalizeSearchResults(out, maxItems)

                // Enrich POS for items found in this database
                enrichPosForLang(q)

                // Check for cancellation before next database
                currentCoroutineContext().ensureActive()
            }

            // Translation search: local first, then RO
            for (tgt in targets) {
                // Build translation database pairs: (translation DB, dictionary DB for lemma lookup)
                val transDatabasePairs = buildList {
                    add(localDbManager.openLocalTranslation() to localDbManager.openLocalDictionary())
                    if (dataDbManager.hasTranslation(lang, tgt)) {
                        add(
                            dataDbManager.openTranslationReadOnly(lang, tgt) to
                                    dataDbManager.openDictionaryReadOnly(lang)
                        )
                    }
                }

                for ((tdb, dictDb) in transDatabasePairs) {
                    val tq = tdb.translationQueries
                    val dq = dictDb.dictionaryQueries
                    val trRows =
                        tq.selectSenseTranslationsByNormalizedSingleWord(lang.code, tgt.code, prefixStart, prefixEnd)
                            .executeAsList()
                    val lemmaRows =
                        dq.selectLemmasByIds(trRows.map { it.lemma_id }).executeAsList().associateBy { it.id }
                    val trRowsSorted = trRows.sortedByDescending { lemmaRows[it.lemma_id]?.zipf_frequency }
                    for (row in trRowsSorted) {
                        // Map translation hit back to a base lemma
                        val lemmaRow = lemmaRows[row.lemma_id]
                        if (lemmaRow != null) {
                            addTranslation(
                                lemmaRow.id,
                                lemmaRow.lemma,
                                row.target_lang_word,
                                lemmaRow.zipf_frequency.toFloat(),
                                lemmaRow.online_only
                            )
                            if (shouldEarlyReturn(dq)) return finalizeSearchResults(out, maxItems)
                        }
                    }

                    // Enrich POS for items found via this translation database
                    enrichPosForLang(dq)
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
        translationTargets: List<Language> = installedTranslationTargets(language),
        senseIds: Set<String>? = null
    ): LanguageCard? = withContext(Dispatchers.IO) {
        // Open dictionary databases in priority order
        val dictDatabases = openDictionaryDatabases(language)
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
                if (!onlineOnly) break // if online only - try to find in local db
            }
        }

        if (lemmaId == null || sourceDb == null) return@withContext null

        val q = sourceDb.dictionaryQueries

        // Get all lemma_pos IDs for this lemma
        val lemmaPosIds = q.selectLemmaPosIdByLemmaId(lemmaId).executeAsList()

        // Open translation databases for all target languages
        val translationDbsMap = translationTargets.associateWith { tgt ->
            openTranslationDatabases(language, tgt)
        }

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
        data class FormData(val formId: Uuid, val form: String)

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
            val forms = formsWithId.map { FormData(it.form_id, it.form) }
            allFormIds.addAll(forms.map { it.formId })

            val sensesRows = q.selectSensesByLemmaPosId(lemmaPosId).executeAsList()
            val filteredSenses = senseIdFilter?.let { filter ->
                sensesRows.filter { filter.contains(it.sense_id.toString()) }
            } ?: sensesRows

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

        if (posDataList.isEmpty()) return@withContext null

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
                // Try databases in order, use the first one that has definitions
                for (transDb in transDbs) {
                    val defs = transDb.translationQueries
                        .selectDefinitionsBySenseIds(allUncachedSenseIds, language.code, tgt.code)
                        .executeAsList()
                    if (defs.isNotEmpty()) {
                        val definitions = defs.associate { it.sense_id to it.definition }

                        val translations = transDb.translationQueries
                            .selectSenseTranslationsBySenseIds(allUncachedSenseIds, language.code, tgt.code)
                            .executeAsList()
                            .groupBy({ it.sense_id }) { row ->
                                LanguageCardTranslation(
                                    targetLangWord = row.target_lang_word,
                                    targetLangSenseClarification = row.target_lang_sense_clarification
                                )
                            }

                        val exampleTrans = transDb.translationQueries
                            .selectExampleTranslationsBySenseIds(allUncachedSenseIds, language.code, tgt.code)
                            .executeAsList()
                            .groupBy({ it.sense_id }) { it }
                            .mapValues { (_, rows) -> rows.associate { it.example_id to it.translation } }

                        return@mapNotNull tgt to TranslationData(definitions, translations, exampleTrans)
                    }
                }
                null
            }.toMap()
        } else emptyMap()

        // Build entries
        val entries = posDataList.map { posData ->
            val forms = posData.forms.map { formData ->
                LanguageCardForm(
                    tags = formTagsMap[formData.formId] ?: emptyList(),
                    form = formData.form
                )
            }

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
                cacheSense(s.senseId.toString(), SenseWithPos(result, posData.pos))
                result
            }

            LanguageCardPosEntry(
                pos = posData.pos,
                forms = forms,
                senses = senses
            )
        }

        if (entries.isEmpty()) return@withContext null

        // Fetch word family from all databases (union)
        val wordFamily = q.selectWordFamilyByLemmaId(lemmaId).executeAsList().toSet()
        // Load related words from all databases (later databases take precedence)
        val relatedWordsMap = loadRelatedWords(
            dictDatabases, language,
            collectAllRelatedWords(entries, wordFamily, lemma)
        )

        LanguageCard(
            entries = entries,
            lemma = lemma,
            zipfFrequency = zipfFrequency,
            wordFamily = wordFamily.toList(),
            relatedWords = relatedWordsMap,
            online = onlineOnly
        )
    }

    /**
     * Gets senses by IDs, using cache when available.
     * Results are automatically cached for future use.
     */
    suspend fun getSenses(
        language: Language,
        lemma: String,
        senseIds: Set<String>,
        translationTargets: List<Language> = installedTranslationTargets(language)
    ): Map<String, SenseWithPos> {
        if (senseIds.isEmpty()) return emptyMap()

        // Check cache first
        val cached = getCachedSenses(senseIds)
        val missingIds = senseIds - cached.keys
        if (missingIds.isEmpty()) return cached

        // Load missing senses (getLanguageCard will cache them automatically)
        val card = getLanguageCard(language, lemma, translationTargets, missingIds) ?: return cached
        val loaded = card.entries.flatMap { entry ->
            entry.senses.map { sense -> sense.senseId to SenseWithPos(sense, entry.pos) }
        }.toMap()

        return cached + loaded
    }

    /**
     * Gets word suggestions for the empty search state.
     * Returns high-frequency words that are not in favorites.
     * Uses a random offset for variety.
     */
    suspend fun getWordSuggestions(
        language: Language,
        count: Int = 5,
        offset: Int = 500
    ): List<String> = withContext(Dispatchers.IO) {
        if (!dataDbManager.hasDictionary(language)) {
            return@withContext emptyList()
        }

        val db = dataDbManager.openDictionaryReadOnly(language)
        val q = db.dictionaryQueries

        // Get favorites to exclude (case-insensitive)
        val favorites = favoritesRepository.getAll()
            .filter { it.language == language }
            .map { it.lemma.lowercase() }
            .toSet()

        val suggestions = mutableListOf<String>()
        val batchSize = 100L
        // Start at a random offset (0 to 500) for variety among high-frequency words
        var offset = (0..offset).random().toLong()
        val maxAttempts = 10

        repeat(maxAttempts) {
            if (suggestions.size >= count) return@repeat

            val batch = q.selectTopFrequentLemmas(language.code, batchSize, offset).executeAsList()
            if (batch.isEmpty()) return@repeat

            for (row in batch) {
                if (suggestions.size >= count) break
                if (row.lemma.lowercase() !in favorites) {
                    suggestions.add(row.lemma)
                }
            }

            offset += batchSize
        }

        suggestions.take(count)
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

        // Search downloaded translation DBs for installed targets
        for (targetLang in installedTargets) {
            currentCoroutineContext().ensureActive()
            if (dataDbManager.hasTranslation(sourceLanguage, targetLang)) {
                val translationQueries = dataDbManager.openTranslationReadOnly(sourceLanguage, targetLang)
                    .translationQueries

                val results =
                    searchSensesByTranslations(translationQueries, sourceLanguage, prefixStart, prefixEnd, senseUuids)
                matchingSenseIds.addAll(results.map { it.toString() })
            }
        }

        currentCoroutineContext().ensureActive()

        // Search the local translation DB
        val localTransDb = localDbManager.openLocalTranslation()
        val translationQueries = localTransDb.translationQueries
        val results = searchSensesByTranslations(translationQueries, sourceLanguage, prefixStart, prefixEnd, senseUuids)
        matchingSenseIds.addAll(results.map { it.toString() })
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
            val localDb = localDbManager.openLocalDictionary()
            val localLemmaIds = localDb.dictionaryQueries
                .selectLemmasByIds(onlineOnlyIds)
                .executeAsList()
                .map { it.id }
                .toSet()

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

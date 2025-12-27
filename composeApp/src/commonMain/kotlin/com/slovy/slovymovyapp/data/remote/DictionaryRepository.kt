package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.dictionary.*
import com.slovy.slovymovyapp.translation.TranslationDatabase
import com.slovy.slovymovyapp.util.stripAccents
import kotlin.uuid.Uuid

internal fun DictionaryPos.toPartOfSpeech(): PartOfSpeech {
    return PartOfSpeech.valueOf(this.name)
}

// Repository that provides search across installed dictionaries and builds LanguageCard by lemma ID,
// aggregating translations from all available target languages.
class DictionaryRepository(
    private val dataDbManager: DataDbManager,
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
    suspend fun search(query: String, dictionaryLanguage: Language? = null, maxItems: Int = 200): List<SearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

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
            val db = dataDbManager.openDictionaryReadOnly(lang)
            val q = db.dictionaryQueries

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

            fun enrichPosForLang() {
                // Fetch POS values for all collected lemma IDs for this language
                val lemmaIds = out.filter { it.language == lang }.map { it.lemmaId }.toSet().toList()
                if (lemmaIds.isNotEmpty()) {
                    val posResults = q.selectLemmaIdAndPosByLemmaIds(lemmaIds).executeAsList()
                    val lemmaIdToPosMap = posResults.groupBy({ it.id }, { it.pos.toPartOfSpeech() })
                    for (i in out.indices) {
                        if (out[i].language == lang) {
                            val posList = lemmaIdToPosMap[out[i].lemmaId] ?: emptyList()
                            out[i] = out[i].copy(pos = posList)
                        }
                    }
                }
            }

            fun shouldEarlyReturn(): Boolean {
                if (out.size >= maxItems) {
                    enrichPosForLang()
                    return true
                }
                return false
            }

            // search exact lemma matches first
            val byWord: List<SelectLemmasByWord> =
                q.selectLemmasByWord(lang.code, trimmed).executeAsList()
            val byNorm: List<SelectLemmasByNormalized> =
                q.selectLemmasByNormalized(lang.code, trimmed).executeAsList()
            byWord.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
            byNorm.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
            if (shouldEarlyReturn()) return out.take(maxItems)

            // search exact form equals (including normalized)
            val formEq: List<SelectLemmasByFormEquals> =
                q.selectLemmasByFormEquals(lang.code, trimmed, maxItems.toLong()).executeAsList()
            val formEqNorm: List<SelectLemmasByFormNormalizedEquals> =
                q.selectLemmasByFormNormalizedEquals(lang.code, trimmed, maxItems.toLong()).executeAsList()
            formEq.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
            formEqNorm.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
            if (shouldEarlyReturn()) return out.take(maxItems)

            // and by prefix later (lemma and forms) - use normalized pattern for GLOB on normalized columns
            val pattern = "${stripAccents(trimmed)}*"
            val lemmaNormLike: List<SelectLemmasNormalizedLike> =
                q.selectLemmasNormalizedLike(lang.code, pattern, maxItems.toLong()).executeAsList()
            lemmaNormLike.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat(), it.online_only) }
            if (shouldEarlyReturn()) return out.take(maxItems)

            val formNormLike: List<SelectLemmasFromFormsNormalizedLike> =
                q.selectLemmasFromFormsNormalizedLike(lang.code, pattern, maxItems.toLong()).executeAsList()
            formNormLike.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat(), it.online_only) }
            if (shouldEarlyReturn()) return out.take(maxItems)

            // search by translation (target language words)
            val targets = installedTranslationTargets(lang)
            for (tgt in targets) {
                val tdb = dataDbManager.openTranslationReadOnly(lang, tgt)
                val tq = tdb.translationQueries
                val trRows =
                    tq.selectSenseTranslationsByNormalizedSingleWord(lang.code, tgt.code, pattern).executeAsList()
                val lemmaRows = q.selectLemmasByIds(trRows.map { it.lemma_id }).executeAsList().associateBy { it.id }
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
                        if (shouldEarlyReturn()) return out.take(maxItems)
                    }
                }
            }

            // Enrich POS for this language before moving to the next
            enrichPosForLang()
        }

        // Check favorite status for all items (single query instead of N queries)
        val result = out.take(maxItems)
        val allFavorites = favoritesRepository.getAll()
        val favoriteItems = allFavorites.map { "${it.targetLang.code}::${it.lemma.lowercase()}" }.toSet()

        return result.map { item ->
            val key = "${item.language.code}::${item.lemma.lowercase()}"
            if (favoriteItems.contains(key)) {
                item.copy(isFavorite = true)
            } else {
                item
            }
        }
    }

    suspend fun getLanguageCard(language: Language, lemma: String): LanguageCard? {
        val db = dataDbManager.openDictionaryReadOnly(language)
        val q = db.dictionaryQueries

        // Collect all base lemma IDs for the given lemma text (case-insensitive), including normalized matches
        // Use firstOrNull to avoid exception if multiple rows exist; query orders by frequency DESC
        val lemmaId = q.selectLemmasByWord(language.code, lemma).executeAsList().firstOrNull()?.id ?: return null

        // Get all lemma_pos IDs for these lemmas
        val lemmaPosIds = LinkedHashSet<Uuid>()

        val posIds = q.selectLemmaPosIdByLemmaId(lemmaId).executeAsList()
        posIds.forEach { lemmaPosIds.add(it) }

        val entries = mutableListOf<LanguageCardPosEntry>()
        var zipfFrequency = 0.0f
        for (lemmaPosId in lemmaPosIds) {
            val lemmaPosRow = q.selectLemmaPosFullById(lemmaPosId).executeAsList().firstOrNull() ?: continue
            zipfFrequency = lemmaPosRow.zipf_frequency.toFloat()
            val formsWithId = q.selectFormsWithIdByLemmaPosId(lemmaPosId).executeAsList()
            val forms = formsWithId.map { formRow ->
                val tags = q.selectFormTagsByFormId(formRow.form_id).executeAsList().map { it.tag }
                LanguageCardForm(tags = tags, form = formRow.form)
            }.toMutableList()

            val sensesRows = q.selectSensesByLemmaPosId(lemmaPosId).executeAsList()
            val senses = sensesRows.map { s ->
                val synonyms = q.selectSenseSynonyms(s.sense_id).executeAsList().map { it.synonym }
                val antonyms = q.selectSenseAntonyms(s.sense_id).executeAsList().map { it.antonym }
                val phrases = q.selectSenseCommonPhrases(s.sense_id).executeAsList().map { it.phrase }
                val traits = q.selectSenseTraits(s.sense_id).executeAsList().map { tr ->
                    LanguageCardTrait(
                        traitType = TraitType.valueOf(tr.trait_type.name),
                        comment = tr.comment
                    )
                }

                // Aggregate per-target language translations/definitions
                val targetLangs = installedTranslationTargets(language)
                val openTdbs: Map<Language, TranslationDatabase> = targetLangs.associateWith { tgt ->
                    val tdb = dataDbManager.openTranslationReadOnly(language, tgt)
                    tdb
                }

                val tgtDefinitions = LinkedHashMap<Language, String>()
                val tgtTranslations = LinkedHashMap<Language, List<LanguageCardTranslation>>()
                for ((tgt, tdb) in openTdbs) {
                    val tq = tdb.translationQueries
                    val def: String? =
                        tq.selectDefinitionsBySense(s.sense_id, language.code, tgt.code).executeAsList().firstOrNull()
                    if (def != null) {
                        tgtDefinitions[tgt] = def
                    }
                    val trs = tq.selectSenseTranslationsBySense(s.sense_id, language.code, tgt.code).executeAsList()
                    if (trs.isNotEmpty()) {
                        tgtTranslations[tgt] = trs.map {
                            LanguageCardTranslation(
                                targetLangWord = it.target_lang_word,
                                targetLangSenseClarification = it.target_lang_sense_clarification
                            )
                        }
                    }
                }

                val examples = q.selectSenseExamples(s.sense_id).executeAsList().map { ex ->
                    val trMap = LinkedHashMap<Language, String>()
                    for ((tgt, tdb) in openTdbs) {
                        val translation: String? =
                            tdb.translationQueries.selectExampleTranslations(
                                s.sense_id,
                                language.code,
                                tgt.code,
                                ex.example_id
                            ).executeAsList()
                                .firstOrNull()
                        if (translation != null) trMap[tgt] = translation
                    }
                    LanguageCardExample(text = ex.text, targetLangTranslations = trMap)
                }

                LanguageCardResponseSense(
                    senseId = s.sense_id.toString(),
                    senseDefinition = s.sense_definition,
                    learnerLevel = LearnerLevel.valueOf(s.learner_level.name),
                    frequency = SenseFrequency.valueOf(s.frequency.name),
                    semanticGroupId = s.semantic_group_id,
                    nameType = s.name_type?.let { NameType.valueOf(it.name) },
                    examples = examples,
                    synonyms = synonyms,
                    antonyms = antonyms,
                    commonPhrases = phrases,
                    traits = traits,
                    targetLangDefinitions = tgtDefinitions,
                    translations = tgtTranslations,
                )
            }

            val entry = LanguageCardPosEntry(
                pos = PartOfSpeech.valueOf(lemmaPosRow.pos.name),
                forms = forms,
                senses = senses
            )
            entries.add(entry)
        }

        // Fetch word family for all collected lemma IDs
        val wordFamily =
            q.selectWordFamilyByLemmaId(lemmaId).executeAsList()

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

        // Add word family
        allRelatedWords.addAll(wordFamily)

        // Remove the main lemma itself (case-insensitive)
        allRelatedWords.removeAll { it.equals(lemma, ignoreCase = true) }

        // Query DB for zipf frequencies
        // Lowercase words for case-insensitive matching
        val relatedWordsMap = if (allRelatedWords.isEmpty()) {
            emptyMap()
        } else {
            q.selectLemmasByWords(language.code, allRelatedWords.map { it.lowercase() })
                .executeAsList()
                .associate { row ->
                    row.lemma to RelatedWord(
                        lemma = row.lemma,
                        zipfFrequency = row.zipf_frequency.toFloat(),
                        online = row.online_only
                    )
                }
        }

        if (entries.isEmpty()) return null
        return LanguageCard(
            entries = entries,
            lemma = lemma,
            zipfFrequency = zipfFrequency,
            wordFamily = wordFamily,
            relatedWords = relatedWordsMap
        )
    }
}

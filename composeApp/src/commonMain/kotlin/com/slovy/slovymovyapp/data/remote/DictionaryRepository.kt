package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlin.uuid.Uuid

internal fun DictionaryPos.toPartOfSpeech(): PartOfSpeech {
    return PartOfSpeech.valueOf(this.name)
}

// Repository that provides search across installed dictionaries and builds LanguageCard by lemma ID,
// aggregating translations from all available target languages.
class DictionaryRepository(
    private val dataDbManager: DataDbManager,
    private val languages: List<Language> = Language.entries,
) {

    data class SearchItem(
        val language: Language,
        val lemmaId: Uuid, // Base lemma ID (not lemma_pos ID)
        val lemma: String,
        val display: String,
        val zipfFrequency: Float,
        val pos: List<PartOfSpeech>
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
    fun search(query: String, dictionaryLanguage: Language? = null, maxItems: Int = 200): List<SearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val langs = if (dictionaryLanguage != null) listOf(dictionaryLanguage) else installedDictionaries()
        if (langs.isEmpty()) {
            // Fallback to simple in-memory filtering for preview/no DB
            return listOf()
        }

        val out = mutableListOf<SearchItem>()
        // Track seen items by language + display string to avoid duplicates
        val seenDisplays = HashSet<String>()
        // Track lemmas that were added as base lemmas to suppress their forms
        val seenLemmas = HashSet<String>()


        for (lang in langs) {
            val db = try {
                dataDbManager.openDictionaryReadOnly(lang)
            } catch (_: Throwable) {
                null
            } ?: continue
            val q = db.dictionaryQueries
            fun addLemma(lemmaId: Uuid, lemma: String, zipfFrequency: Float) {
                val key = "${lang.code}::$lemma"
                if (!seenDisplays.contains(key)) {
                    out.add(
                        SearchItem(
                            language = lang,
                            lemmaId = lemmaId,
                            lemma = lemma,
                            display = lemma,
                            zipfFrequency = zipfFrequency,
                            pos = emptyList()
                        )
                    )
                    seenDisplays.add(key)
                    seenLemmas.add("${lang.code}::${lemma.lowercase()}")
                }
            }

            fun addForm(lemmaId: Uuid, lemma: String, form: String, zipfFrequency: Float) {
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
                            pos = emptyList()
                        )
                    )
                    seenDisplays.add(key)
                }
            }


            // search exact matching first
            val byWord: List<com.slovy.slovymovyapp.dictionary.SelectLemmasByWord> =
                q.selectLemmasByWord(trimmed).executeAsList()
            val byNorm: List<com.slovy.slovymovyapp.dictionary.SelectLemmasByNormalized> =
                q.selectLemmasByNormalized(trimmed).executeAsList()
            byWord.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat()) }
            byNorm.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat()) }

            val formEq: List<com.slovy.slovymovyapp.dictionary.SelectLemmasByFormEquals> =
                q.selectLemmasByFormEquals(trimmed, maxItems.toLong()).executeAsList()
            val formEqNorm: List<com.slovy.slovymovyapp.dictionary.SelectLemmasByFormNormalizedEquals> =
                q.selectLemmasByFormNormalizedEquals(trimmed, maxItems.toLong()).executeAsList()
            formEq.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat()) }
            formEqNorm.forEach {
                addForm(
                    it.id,
                    it.lemma,
                    it.form,
                    it.zipf_frequency.toFloat()
                )
            }

            // and by prefix later
            val pattern = "$trimmed%"
            val lemmaLike: List<com.slovy.slovymovyapp.dictionary.SelectLemmasLike> =
                q.selectLemmasLike(pattern, maxItems.toLong()).executeAsList()
            val lemmaNormLike: List<com.slovy.slovymovyapp.dictionary.SelectLemmasNormalizedLike> =
                q.selectLemmasNormalizedLike(pattern, maxItems.toLong()).executeAsList()
            lemmaLike.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat()) }
            lemmaNormLike.forEach { addLemma(it.id, it.lemma, it.zipf_frequency.toFloat()) }

            val formLike: List<com.slovy.slovymovyapp.dictionary.SelectLemmasFromFormsLike> =
                q.selectLemmasFromFormsLike(pattern, maxItems.toLong()).executeAsList()
            val formNormLike: List<com.slovy.slovymovyapp.dictionary.SelectLemmasFromFormsNormalizedLike> =
                q.selectLemmasFromFormsNormalizedLike(pattern, maxItems.toLong()).executeAsList()
            formLike.forEach { addForm(it.id, it.lemma, it.form, it.zipf_frequency.toFloat()) }
            formNormLike.forEach {
                addForm(
                    it.id,
                    it.lemma,
                    it.form,
                    it.zipf_frequency.toFloat()
                )
            }

            // Fetch POS values for all collected lemma IDs
            val lemmaIds = out.filter { it.language == lang }.map { it.lemmaId }.toSet().toList()
            if (lemmaIds.isNotEmpty()) {
                val posResults = q.selectLemmaIdAndPosByLemmaIds(lemmaIds).executeAsList()
                val lemmaIdToPosMap = posResults.groupBy({ it.id }, { it.pos.toPartOfSpeech() })

                // Update search items with their POS lists
                for (i in out.indices) {
                    if (out[i].language == lang) {
                        val posList = lemmaIdToPosMap[out[i].lemmaId] ?: emptyList()
                        out[i] = out[i].copy(pos = posList)
                    }
                }
            }
        }

        return out.take(maxItems)
    }

    fun getLanguageCard(language: Language, lemma: String): LanguageCard? {
        val db = dataDbManager.openDictionaryReadOnly(language)
        val q = db.dictionaryQueries

        // Collect all base lemma IDs for the given lemma text (case-insensitive), including normalized matches
        val byWord = q.selectLemmasByWord(lemma).executeAsList()
        val byNorm = q.selectLemmasByNormalized(lemma).executeAsList()
        val lemmaIds = LinkedHashSet<Uuid>()
        byWord.forEach { lemmaIds.add(it.id) }
        byNorm.forEach { lemmaIds.add(it.id) }
        if (lemmaIds.isEmpty()) return null

        // Get all lemma_pos IDs for these lemmas
        val lemmaPosIds = LinkedHashSet<Uuid>()
        lemmaIds.forEach { lemmaId ->
            val posIds = q.selectLemmaPosIdByLemmaId(lemmaId).executeAsList()
            posIds.forEach { lemmaPosIds.add(it) }
        }

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
                    val def: String? = tq.selectDefinitionsBySense(s.sense_id).executeAsList().firstOrNull()
                    if (def != null) {
                        tgtDefinitions[tgt] = def
                    }
                    val trs = tq.selectSenseTranslationsBySense(s.sense_id).executeAsList()
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
                            tdb.translationQueries.selectExampleTranslations(s.sense_id, ex.example_id).executeAsList()
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
        if (entries.isEmpty()) return null
        return LanguageCard(entries = entries, lemma = lemma, zipfFrequency = zipfFrequency)
    }
}

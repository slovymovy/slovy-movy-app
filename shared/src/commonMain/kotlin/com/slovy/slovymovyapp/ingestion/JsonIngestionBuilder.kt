@file:OptIn(ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.*
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryQueries
import com.slovy.slovymovyapp.translation.TranslationDatabase
import com.slovy.slovymovyapp.translation.TranslationQueries
import com.slovy.slovymovyapp.util.md5
import com.slovy.slovymovyapp.util.stripAccents
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.slovy.slovymovyapp.data.dictionary.TraitType as DictTraitType


/**
 * Shared mapping of language code to the preferred native raw-extract source filename.
 * Used by ingestion code and tests to consistently select the native wiktextract source.
 */
val LANG_TO_SOURCE_FILE: Map<String, String> = mapOf(
    "en" to "raw-wiktextract-data.jsonl",
    "ru" to "ru-extract.jsonl",
    "nl" to "nl-extract.jsonl",
    "pl" to "pl-extract.jsonl",
)

/**
 * Builder for ingesting processed JSON and raw extracted data into dictionary and translation databases.
 *
 * This class is KMP-compatible and produces deterministic results across all platforms:
 * - Lemma IDs are derived from MD5 hash of lemma + normalized lemma (RFC 1321 compliant)
 * - All other IDs come from the input JSON
 * - Iteration order is preserved for consistent database insertion
 *
 * @param translationDbProvider The database provider for opening translation databases
 * @param frequencyMap Map of lemma words to their Zipf frequency values
 * @param warningLogger Optional warning sink for recoverable ingestion anomalies
 */
class JsonIngestionBuilder(
    private val translationDbProvider: (from: String, to: String) -> TranslationDatabase,
    private val frequencyMap: Map<String, Double>,
    private val warningLogger: (String) -> Unit = {},
) {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

    /**
     * Input payload for ingestion. When [processedJson] is null, only raw ingestion is performed.
     */
    data class IngestionInput(
        val rawJson: String,
        val processedJson: String? = null
    )

    /**
     * Ingests only the raw JSON data for a word.
     *
     * Used for words that do not yet have processed LanguageCardResponse content.
     * Inserts the lemma, lemma_pos and forms, and marks the lemma as `online_only = true`
     * so the app knows to fetch details remotely.
     *
     * @param rawJson JSON string containing the raw ExtractedWordData
     * @throws IllegalArgumentException if lemma not found in frequency map or already exists
     */
    fun ingestRawOnly(
        rawJson: String, dictDb: DictionaryDatabase
    ) {
        ingestBatch(listOf(IngestionInput(rawJson = rawJson)), dictDb)
    }

    /**
     * Ingests processed and raw JSON data into the dictionary and translation databases.
     *
     * @param processedJson JSON string containing the processed LanguageCardResponse
     * @param rawJson JSON string containing the raw ExtractedWordData
     * @return List of POS entries that were skipped because they weren't in the raw ingestion
     * @throws IllegalArgumentException if lemma not found in frequency map, duplicate IDs, or lemma already exists
     */
    fun ingest(
        processedJson: String, rawJson: String, dictDb: DictionaryDatabase
    ): List<String> {
        val raw = json.decodeFromString(ExtractedWordData.serializer(), rawJson)
        val processed = json.decodeFromString(LanguageCardResponse.serializer(), processedJson)

        val (skipped, translationOps) = dictDb.transactionWithResult {
            val dictQ = dictDb.dictionaryQueries
            ingestRawOnlyInternal(raw, dictQ)
            ingestProcessedOverRawInternal(processed, raw.word, raw.langCode, dictQ)
        }

        translationOps.forEach { (target, actions) ->
            val trDb = translationDbProvider(raw.langCode, target)
            trDb.transaction { actions.forEach { op -> trDb.translationQueries.op() } }
        }
        return skipped
    }

    /**
     * Ingests a batch of words inside a single dictionary transaction and per-target translation transactions.
     * Translation operations are grouped by target language to reduce commit overhead.
     */
    fun ingestBatch(inputs: List<IngestionInput>, dictDb: DictionaryDatabase) {
        if (inputs.isEmpty()) return
        val parsedInputs = inputs.map { input ->
            ParsedIngestionInput(
                raw = json.decodeFromString(ExtractedWordData.serializer(), input.rawJson),
                processed = input.processedJson?.let {
                    json.decodeFromString(LanguageCardResponse.serializer(), it)
                }
            )
        }
        val sourceLang = parsedInputs.first().raw.langCode
        require(parsedInputs.all { it.raw.langCode == sourceLang }) {
            "Ingestion batch must contain words for a single language"
        }
        val translationOpsByTarget = mutableMapOf<String, MutableList<TranslationQueries.() -> Unit>>()
        val dictQ = dictDb.dictionaryQueries

        dictDb.transaction {
            parsedInputs.forEach { payload ->
                ingestRawOnlyInternal(payload.raw, dictQ)
                if (payload.processed != null) {
                    val (_, ops) = ingestProcessedOverRawInternal(
                        payload.processed, payload.raw.word, payload.raw.langCode, dictQ
                    )
                    ops.forEach { (target, actions) ->
                        translationOpsByTarget.getOrPut(target) { mutableListOf() }.addAll(actions)
                    }
                }
            }
        }

        translationOpsByTarget.forEach { (target, actions) ->
            val trDb = translationDbProvider(sourceLang, target)
            val trQ = trDb.translationQueries
            trDb.transaction {
                actions.forEach { op -> trQ.op() }
            }
        }
    }

    /**
     * Adds processed JSON data to an already-ingested raw-only word.
     *
     * This method is used when a word was previously ingested with [ingestRawOnly] (with online_only=true)
     * and now needs to have processed data added to it.
     *
     * @param processedJson JSON string containing the processed LanguageCardResponse
     * @param word The lemma word (used to generate deterministic ID)
     * @param langCode The source language code
     * @param dictDb The dictionary database
     * @return List of POS entries that were skipped because they weren't in the raw ingestion
     * @throws IllegalArgumentException if lemma doesn't exist or already has processed data (online_only=false)
     */
    fun ingestProcessedOverRaw(
        processedJson: String,
        word: String,
        langCode: String,
        dictDb: DictionaryDatabase
    ): List<String> {
        val processed = json.decodeFromString(LanguageCardResponse.serializer(), processedJson)

        // Use transactionWithResult to get the return value
        val result = dictDb.transactionWithResult {
            ingestProcessedOverRawInternal(processed, word, langCode, dictDb.dictionaryQueries)
        }

        result.second.forEach { (target, actions) ->
            val trDb = translationDbProvider(langCode, target)
            val trQ = trDb.translationQueries
            trDb.transaction {
                actions.forEach { op -> trQ.op() }
            }
        }

        return result.first
    }

    /**
     * Adds translations to an already processed word (with senses).
     *
     * This method is used when a word already has full processed data (online_only=false)
     * and needs additional translations for new target languages.
     *
     * @param processedJson JSON string containing the processed LanguageCardResponse with translations
     * @param word The lemma word (used to generate deterministic ID)
     * @param langCode The source language code
     * @param dictDb The dictionary database
     * @throws IllegalArgumentException if lemma doesn't exist or is online_only (needs senses first)
     */
    fun ingestTranslationsOnly(
        processedJson: String,
        word: String,
        langCode: String,
        dictDb: DictionaryDatabase
    ) {
        val processed = json.decodeFromString(LanguageCardResponse.serializer(), processedJson)

        val translationOpsByTarget =
            ingestTranslationsOnlyInternal(processed, word, langCode, dictDb.dictionaryQueries)

        translationOpsByTarget.forEach { (target, actions) ->
            val trDb = translationDbProvider(langCode, target)
            val trQ = trDb.translationQueries
            trDb.transaction {
                actions.forEach { op -> trQ.op() }
            }
        }
    }

    private data class ParsedIngestionInput(
        val raw: ExtractedWordData,
        val processed: LanguageCardResponse?
    )

    /**
     * Maps each raw entry to the lemma_pos cluster it belongs to.
     *
     * [entries] is an ordered list of (pos, lemmaPosId) pairs — one per cluster.
     * [entryIdToLemmaPosId] maps every raw entry_id to the lemma_pos_id of its cluster.
     * [primaryIdForPos] returns the first lemma_pos_id for a given POS (the cluster with most forms).
     */
    private data class LemmaPosMapping(
        val entries: List<Pair<DictionaryPos, Uuid>>,
        val entryIdToLemmaPosId: Map<Uuid, Uuid>
    ) {
        fun primaryIdForPos(pos: DictionaryPos): Uuid? =
            entries.firstOrNull { it.first == pos }?.second
    }

    /** Carries an entry together with its source-file key from source_file_to_entries. */
    private data class EntryWithSourceFile(val entry: ExtractedWordEntry, val sourceFile: String)

    /** Carries an entry together with the FormSource enum used when inserting forms. */
    private data class EntryWithSource(val entry: ExtractedWordEntry, val source: FormSource)

    private data class EntriesSelection(
        val nativeEntries: List<ExtractedWordEntry>,
        val enWiktionaryEntries: List<ExtractedWordEntry>,
        val allEntries: List<ExtractedWordEntry>,
        val allEntriesWithSourceFile: List<EntryWithSourceFile>,
        val entriesForForms: List<EntryWithSource>
    )

    private data class FormKey(
        val form: String,
        val formNormalized: String,
        val tags: Set<String>,
        val source: FormSource
    )

    private fun selectEntries(raw: ExtractedWordData): EntriesSelection {
        val nativeKey = LANG_TO_SOURCE_FILE[raw.langCode]!!
        val enWiktionarySourceKey = LANG_TO_SOURCE_FILE[Language.ENGLISH.code]!!

        val nativeEntries = raw.sourceFileToEntries[nativeKey] ?: emptyList()
        val enWiktionaryEntries = raw.sourceFileToEntries[enWiktionarySourceKey] ?: emptyList()

        val allEntries = raw.sourceFileToEntries.values.flatten()

        val allEntriesWithSourceFile = raw.sourceFileToEntries.flatMap { (src, entries) ->
            entries.map { EntryWithSourceFile(it, src) }
        }

        // For English words the native source IS the EN wiktionary — only one source.
        // For all other languages, import forms from both native and EN wiktionary so
        // forms unique to either source are preserved.
        val isSameSrc = raw.langCode == Language.ENGLISH.code
        val entriesForForms: List<EntryWithSource> = buildList {
            nativeEntries.forEach { add(EntryWithSource(it, FormSource.NATIVE)) }
            if (!isSameSrc) {
                enWiktionaryEntries.forEach { add(EntryWithSource(it, FormSource.EN)) }
            }
        }

        return EntriesSelection(
            nativeEntries = nativeEntries,
            enWiktionaryEntries = enWiktionaryEntries,
            allEntries = allEntries,
            allEntriesWithSourceFile = allEntriesWithSourceFile,
            entriesForForms = entriesForForms
        )
    }

    /**
     * Builds a [LemmaPosMapping] by clustering raw entries per POS.
     *
     * Each native entry with forms is its own cluster root. Native entries without forms and
     * non-native entries are absorbed into the best-matching cluster via Jaccard similarity.
     *
     * @param lang The language code of the word
     * @param entriesSelection The parsed entries from the raw JSON
     */
    private fun collectLemmaPosMapping(
        lang: String,
        entriesSelection: EntriesSelection,
    ): LemmaPosMapping {
        val nativeSource = LANG_TO_SOURCE_FILE[lang]!!

        val allLemmaPosEntries = mutableListOf<Pair<DictionaryPos, Uuid>>()
        val entryIdToLemmaPosId = mutableMapOf<Uuid, Uuid>()

        // Group all entries by POS, skipping unknown POS
        val byPos = entriesSelection.allEntriesWithSourceFile
            .groupBy { mapPos(it.entry.pos) }
            .filterKeys { it != null }
            .mapKeys { it.key!! }

        byPos.forEach { (pos, entriesForPos) ->
            val nativeWithForms = entriesForPos.filter { it.sourceFile == nativeSource && it.entry.forms.isNotEmpty() }
            val nativeNoForms = entriesForPos.filter { it.sourceFile == nativeSource && it.entry.forms.isEmpty() }
            val nonNativeEntries = entriesForPos.filter { it.sourceFile != nativeSource }

            // All native entries with forms are active roots (raw data alone determines clustering)
            val activeRootCandidates: List<ExtractedWordEntry> = nativeWithForms.map { it.entry }
            // Merge roots with identical raw form sets — splitting adds no value when paradigms are identical.
            // Use raw form texts (not accent-stripped) so entries with different stress markers
            // (e.g., Dutch vóórkomen vs voorkómen) are correctly kept as separate clusters.
            val activeRoots: List<ExtractedWordEntry> = activeRootCandidates
                .groupBy { entry -> entry.forms.map { it.form }.toSet() }
                .values
                .map { group ->
                    group.sortedWith(
                        compareByDescending<ExtractedWordEntry> { it.forms.size }
                            .thenBy { it.entryId.toString() }
                    ).first()
                }

            // Inactive native entries (nativeWithForms not chosen as roots) → absorbed into primary
            val activeRootIds = activeRoots.map { it.entryId }.toHashSet()
            val inactiveNativeWithForms = nativeWithForms
                .filter { it.entry.entryId !in activeRootIds }
                .map { it.entry }

            if (activeRoots.isEmpty()) {
                // Fallback: single cluster from nativeNoForms or nonNativeEntries
                val fallbackEntries = nativeNoForms + nonNativeEntries
                val root = fallbackEntries.minByOrNull { it.entry.entryId.toString() }?.entry
                    ?: return@forEach  // no entries for this POS (shouldn't happen)
                val lemmaPosId = uuidParse(root.entryId.toString())
                allLemmaPosEntries += pos to lemmaPosId
                fallbackEntries.forEach { e ->
                    entryIdToLemmaPosId[uuidParse(e.entry.entryId.toString())] = lemmaPosId
                }
            } else {
                // Primary cluster = root with most forms; tie-break: alphabetically first entryId
                val primaryRoot = activeRoots.sortedWith(
                    compareByDescending<ExtractedWordEntry> { it.forms.size }
                        .thenBy { it.entryId.toString() }
                ).first()
                val primaryLemmaPosId = uuidParse(primaryRoot.entryId.toString())

                // Register primary cluster first so primaryIdForPos returns it
                allLemmaPosEntries += pos to primaryLemmaPosId
                entryIdToLemmaPosId[primaryLemmaPosId] = primaryLemmaPosId

                // Register remaining active roots (each as its own cluster)
                activeRoots.filter { it.entryId != primaryRoot.entryId }.forEach { root ->
                    val id = uuidParse(root.entryId.toString())
                    allLemmaPosEntries += pos to id
                    entryIdToLemmaPosId[id] = id
                }

                // Absorb inactive native entries into primary
                inactiveNativeWithForms.forEach { e ->
                    entryIdToLemmaPosId[uuidParse(e.entryId.toString())] = primaryLemmaPosId
                }
                nativeNoForms.forEach { ewsf ->
                    entryIdToLemmaPosId[uuidParse(ewsf.entry.entryId.toString())] = primaryLemmaPosId
                }

                // Assign non-native entries to best cluster via Jaccard similarity
                val clusterRoots = activeRoots
                nonNativeEntries.forEach { ewsf ->
                    val nonNativeForms = formSet(ewsf.entry)
                    val assignedId = if (nonNativeForms.isEmpty()) {
                        primaryLemmaPosId
                    } else {
                        val best = clusterRoots.maxByOrNull { jaccardSimilarity(formSet(it), nonNativeForms) }
                        if (best == null || jaccardSimilarity(formSet(best), nonNativeForms) == 0.0) {
                            primaryLemmaPosId
                        } else {
                            uuidParse(best.entryId.toString())
                        }
                    }
                    entryIdToLemmaPosId[uuidParse(ewsf.entry.entryId.toString())] = assignedId
                }
            }
        }

        return LemmaPosMapping(allLemmaPosEntries, entryIdToLemmaPosId)
    }

    private fun formSet(entry: ExtractedWordEntry): Set<String> =
        entry.forms.map { stripAccents(it.form) }.toSet()

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val intersection = a.intersect(b).size
        val union = (a + b).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private fun ingestRawOnlyInternal(
        raw: ExtractedWordData,
        dictQ: DictionaryQueries
    ) {
        val langCode = raw.langCode
        val lemmaWord = raw.word
        val zipfFrequency = frequencyMap[lemmaWord]
            ?: throw IllegalArgumentException("Lemma '$lemmaWord' not found in frequency map")

        val lemmaNormalized = stripAccents(lemmaWord)
        val entriesSelection = selectEntries(raw)

        val baseLemmaId = generateLemmaId(lemmaWord, lemmaNormalized)

        val selectLemmasById = dictQ.selectLemmasById(baseLemmaId).executeAsOneOrNull()
        if (selectLemmasById != null) {
            throw IllegalArgumentException("Lemma '$lemmaWord' already exists in database")
        }

        dictQ.insertLemma(
            id = baseLemmaId,
            lang_code = langCode,
            lemma = lemmaWord,
            lemma_normalized = lemmaNormalized,
            zipf_frequency = zipfFrequency,
            online_only = true
        )

        // Include POS even when the entry has no forms to avoid skipping POS in processed-over-raw ingestion.
        val lemmaPosMapping = collectLemmaPosMapping(langCode, entriesSelection)

        lemmaPosMapping.entries.forEach { (pos, lemmaPosId) ->
            dictQ.insertLemmaPos(
                id = lemmaPosId,
                lemma_id = baseLemmaId,
                pos = pos
            )
        }

        val lemmaPosIdToForms = buildLemmaPosIdToForms(
            entriesSelection.entriesForForms,
            lemmaPosMapping.entryIdToLemmaPosId
        )
        insertForms(dictQ, lemmaPosIdToForms)

        // Persist sense→lemma_pos routing hints for ingestProcessedOverRaw.
        // Validate first: duplicate sense_ids across raw entries are a data error.
        val seenSenseIds = mutableSetOf<Uuid>()
        entriesSelection.allEntriesWithSourceFile.forEach { ewsf ->
            ewsf.entry.senses.forEach { sense ->
                val senseId = uuidParse(sense.senseId.toString())
                require(seenSenseIds.add(senseId)) {
                    "Duplicate sense_id '${sense.senseId}' in raw data for lemma '${raw.word}'"
                }
            }
        }
        entriesSelection.allEntriesWithSourceFile.forEach { ewsf ->
            val entryId = uuidParse(ewsf.entry.entryId.toString())
            val lemmaPosId = lemmaPosMapping.entryIdToLemmaPosId[entryId] ?: return@forEach
            ewsf.entry.senses.forEach { sense ->
                dictQ.insertLemmaPosHint(
                    sense_id = uuidParse(sense.senseId.toString()),
                    lemma_pos_id = lemmaPosId
                )
            }
        }
    }

    private fun ingestProcessedOverRawInternal(
        processed: LanguageCardResponse,
        word: String,
        langCode: String,
        dictQ: DictionaryQueries
    ): Pair<List<String>, Map<String, List<TranslationQueries.() -> Unit>>> {
        val lemmaNormalized = stripAccents(word)
        val baseLemmaId = generateLemmaId(word, lemmaNormalized)

        // Verify lemma exists and is online_only
        val existingLemma = dictQ.selectLemmasById(baseLemmaId).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Lemma '$word' not found in database")

        if (!existingLemma.online_only) {
            throw IllegalArgumentException("Lemma '$word' already has processed data (online_only=false)")
        }

        // Get existing lemma_pos entries and build LemmaPosMapping (first per POS = primary)
        val existingLemmaPos = dictQ.selectLemmaPosByLemmaId(baseLemmaId).executeAsList()
        val posToFirstId = mutableMapOf<DictionaryPos, Uuid>()
        existingLemmaPos.forEach { lp -> if (lp.pos !in posToFirstId) posToFirstId[lp.pos] = lp.id }
        val lemmaPosMapping = LemmaPosMapping(
            entries = existingLemmaPos.map { lp -> lp.pos to lp.id },
            entryIdToLemmaPosId = existingLemmaPos.associate { lp -> lp.id to (posToFirstId[lp.pos] ?: lp.id) }
        )

        // Update online_only to false
        dictQ.updateLemmaOnlineOnly(online_only = false, id = baseLemmaId)

        // Insert word family (excluding the lemma itself)
        processed.wordFamily?.forEach { familyWord ->
            if (!familyWord.equals(word, ignoreCase = true)) {
                dictQ.insertLemmaWordFamily(lemma_id = baseLemmaId, word = familyWord)
            }
        }

        // Build per-sense routing map from hints written during raw-only ingestion
        val senseIdToLemmaPosId = loadSenseIdToLemmaPosId(processed, dictQ)
        val skippedPos = insertSensesAndRelatedData(
            processed, senseIdToLemmaPosId, lemmaPosMapping, dictQ, word, skipMissingPos = true
        )
        if (skippedPos.isNotEmpty()) {
            warningLogger("Skipped POS entries for lemma '$word': ${skippedPos.joinToString()}")
        }

        val translationOps = buildTranslationOperations(
            processed, senseIdToLemmaPosId, lemmaPosMapping, baseLemmaId, langCode, skipMissingPos = true
        )
        return Pair(skippedPos, translationOps)
    }

    private fun ingestTranslationsOnlyInternal(
        processed: LanguageCardResponse,
        word: String,
        langCode: String,
        dictQ: DictionaryQueries
    ): Map<String, List<TranslationQueries.() -> Unit>> {
        val lemmaNormalized = stripAccents(word)
        val baseLemmaId = generateLemmaId(word, lemmaNormalized)

        // Verify lemma exists and has processed data
        val existingLemma = dictQ.selectLemmasById(baseLemmaId).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Lemma '$word' not found in database")

        if (existingLemma.online_only) {
            throw IllegalArgumentException("Lemma '$word' is online_only - needs senses before adding translations")
        }

        // Get existing lemma_pos entries and build LemmaPosMapping (first per POS = primary)
        val existingLemmaPos = dictQ.selectLemmaPosByLemmaId(baseLemmaId).executeAsList()
        val posToFirstId = mutableMapOf<DictionaryPos, Uuid>()
        existingLemmaPos.forEach { lp -> if (lp.pos !in posToFirstId) posToFirstId[lp.pos] = lp.id }
        val lemmaPosMapping = LemmaPosMapping(
            entries = existingLemmaPos.map { lp -> lp.pos to lp.id },
            entryIdToLemmaPosId = existingLemmaPos.associate { lp -> lp.id to (posToFirstId[lp.pos] ?: lp.id) }
        )

        val senseIdToLemmaPosId = loadSenseIdToLemmaPosId(processed, dictQ)
        return buildTranslationOperations(
            processed, senseIdToLemmaPosId, lemmaPosMapping, baseLemmaId, langCode, skipMissingPos = true
        )
    }

    private fun loadSenseIdToLemmaPosId(
        processed: LanguageCardResponse,
        dictQ: DictionaryQueries
    ): Map<Uuid, Uuid> {
        val allSenseIds = processed.entries.flatMap { entry -> entry.senses }.map { sense -> uuidParse(sense.senseId) }
        if (allSenseIds.isEmpty()) {
            return emptyMap()
        }
        return dictQ.selectLemmaPosHintsBySenseIds(allSenseIds).executeAsList()
            .associate { it.sense_id to it.lemma_pos_id }
    }

    private fun buildTranslationOperations(
        processed: LanguageCardResponse,
        senseIdToLemmaPosId: Map<Uuid, Uuid>,
        lemmaPosMapping: LemmaPosMapping,
        baseLemmaId: Uuid,
        sourceLangCode: String,
        skipMissingPos: Boolean = false
    ): Map<String, List<TranslationQueries.() -> Unit>> {
        val operations = mutableMapOf<String, MutableList<TranslationQueries.() -> Unit>>()
        val targetLangs = collectTargetLanguages(processed)
        targetLangs.forEach { trg ->
            val opsForTarget = operations.getOrPut(trg) { mutableListOf() }
            processed.entries.forEach { posEntry ->
                val pos = mapPos(posEntry.pos)
                val defaultLemmaPosId = if (pos != null) lemmaPosMapping.primaryIdForPos(pos) else null
                if (defaultLemmaPosId == null && skipMissingPos) {
                    return@forEach
                }
                posEntry.senses.forEach { sense ->
                    val senseId = uuidParse(sense.senseId)
                    val lemmaPosIdForSense = resolveLemmaPosIdForSense(
                        senseId = senseId,
                        posLabel = posEntry.pos,
                        defaultLemmaPosId = defaultLemmaPosId,
                        senseIdToLemmaPosId = senseIdToLemmaPosId
                    )
                    val def = sense.targetLangDefinitions[trg]
                    if (def != null) {
                        opsForTarget += {
                            insertSenseTargetDefinition(
                                sense_id = senseId,
                                from_lang_code = sourceLangCode,
                                target_lang_code = trg,
                                definition = def
                            )
                        }
                    }
                    val translations = sense.translations[trg] ?: emptyList()
                    translations.forEachIndexed { idx, t ->
                        opsForTarget += {
                            insertSenseTranslation(
                                senseId,
                                sourceLangCode,
                                trg,
                                idx.toLong(),
                                t.targetLangWord,
                                stripAccents(t.targetLangWord),
                                t.targetLangSenseClarification,
                                baseLemmaId,
                                lemmaPosIdForSense
                            )
                        }
                    }
                    sense.examples.forEachIndexed { exIdx, ex ->
                        val exTr = ex.targetLangTranslations[trg]
                        if (exTr != null) {
                            opsForTarget += {
                                insertExampleTranslation(
                                    sense_id = senseId,
                                    from_lang_code = sourceLangCode,
                                    target_lang_code = trg,
                                    example_id = exIdx.toLong(),
                                    translation = exTr
                                )
                            }
                        }
                    }
                }
            }
        }
        return operations
    }

    /**
     * Inserts senses and all related data (traits, synonyms, antonyms, phrases, examples).
     *
     * @param processed The processed JSON data
     * @param senseIdToLemmaPosId Per-sense lemma_pos routing; falls back to POS primary only for single-cluster POS
     * @param lemmaPosMapping Provides primaryIdForPos and cluster counts used when sense is not in senseIdToLemmaPosId
     * @param dictQ Dictionary queries
     * @param skipMissingPos If true, skip entries where POS has no lemma_pos; if false, throw
     * @return List of POS entries that were skipped (only when skipMissingPos is true)
     */
    private fun insertSensesAndRelatedData(
        processed: LanguageCardResponse,
        senseIdToLemmaPosId: Map<Uuid, Uuid>,
        lemmaPosMapping: LemmaPosMapping,
        dictQ: DictionaryQueries,
        lemmaWord: String,
        skipMissingPos: Boolean = false
    ): List<String> {
        val skippedPos = mutableListOf<String>()

        processed.entries.forEach { posEntry ->
            val pos = mapPos(posEntry.pos)
            val defaultLemmaPosId = if (pos != null) lemmaPosMapping.primaryIdForPos(pos) else null
            if (defaultLemmaPosId == null && skipMissingPos) {
                skippedPos.add(posEntry.pos)
                return@forEach
            }

            posEntry.senses.forEach { sense ->
                val senseId = uuidParse(sense.senseId)
                val lemmaPosIdForSense = resolveLemmaPosIdForSense(
                    senseId = senseId,
                    posLabel = posEntry.pos,
                    defaultLemmaPosId = defaultLemmaPosId,
                    senseIdToLemmaPosId = senseIdToLemmaPosId
                )
                dictQ.insertSense(
                    sense_id = senseId,
                    lemma_pos_id = lemmaPosIdForSense,
                    sense_definition = sense.senseDefinition,
                    learner_level = mapLevel(sense.learnerLevel),
                    frequency = mapFrequency(sense.frequency),
                    semantic_group_id = sense.semanticGroupId,
                    name_type = mapNameType(sense.nameType)
                )
                // traits
                sense.traits.forEach { t ->
                    if (t.traitType == TraitType.UNKNOWN) {
                        return@forEach
                    }
                    dictQ.insertSenseTrait(
                        sense_id = senseId,
                        trait_type = mapTraitType(t.traitType),
                        comment = t.comment
                    )
                }
                // synonyms (excluding the lemma itself)
                sense.synonyms.forEach { syn ->
                    if (!syn.equals(lemmaWord, ignoreCase = true)) {
                        dictQ.insertSenseSynonym(sense_id = senseId, synonym = syn)
                    }
                }
                // antonyms (excluding the lemma itself)
                sense.antonyms.forEach { ant ->
                    if (!ant.equals(lemmaWord, ignoreCase = true)) {
                        dictQ.insertSenseAntonym(sense_id = senseId, antonym = ant)
                    }
                }
                // common phrases
                sense.commonPhrases.forEach { phrase ->
                    dictQ.insertSenseCommonPhrase(sense_id = senseId, phrase = phrase)
                }
                // examples (store index-based id)
                sense.examples.forEachIndexed { exIdx, ex ->
                    dictQ.insertSenseExample(sense_id = senseId, example_id = exIdx.toLong(), text = ex.text)
                }
            }
        }

        return skippedPos
    }

    private fun resolveLemmaPosIdForSense(
        senseId: Uuid,
        posLabel: String,
        defaultLemmaPosId: Uuid?,
        senseIdToLemmaPosId: Map<Uuid, Uuid>
    ): Uuid {
        senseIdToLemmaPosId[senseId]?.let { return it }
        // No hint found — fall back to the primary cluster for this POS.
        // This can happen when a processed sense_id has no counterpart in the raw data
        // (e.g. a new sense added only in the processed JSON). Using the primary cluster
        // is a best-effort assignment; correctness requires matching sense_ids in raw data.
        return defaultLemmaPosId ?: error("POS $posLabel not found in lemmaPosMapping")
    }

    /**
     * Copies raw data (lemma, lemma_pos, forms) from source database to target database.
     * Used to replicate downloaded DB data into local DB before ingesting processed data.
     *
     * This method is idempotent - if the lemma already exists in the target DB, it does nothing.
     *
     * @param word The lemma word
     * @param langCode The language code
     * @param sourceDb The source database (e.g., downloaded read-only DB)
     * @param targetDb The target database (e.g., local writable DB)
     * @param frequency The Zipf frequency for the lemma
     */
    fun copyRawDataToLocal(
        word: String,
        langCode: String,
        sourceDb: DictionaryDatabase,
        targetDb: DictionaryDatabase,
        frequency: Double,
        posFilter: Set<String>? = null
    ) {
        val lemmaNormalized = stripAccents(word)
        val lemmaId = generateLemmaId(word, lemmaNormalized)

        val sourceQ = sourceDb.dictionaryQueries
        val targetQ = targetDb.dictionaryQueries

        // Check if already in target DB - skip if exists (idempotent)
        if (targetQ.selectLemmasById(lemmaId).executeAsOneOrNull() != null) {
            return
        }

        // Look up lemma in source DB
        val sourceLemma = sourceQ.selectLemmasById(lemmaId).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Lemma '$word' not found in source database")

        targetDb.transaction {
            // Copy lemma entry (preserve online_only status from source)
            targetQ.insertLemma(
                id = lemmaId,
                lang_code = langCode,
                lemma = word,
                lemma_normalized = lemmaNormalized,
                zipf_frequency = frequency,
                online_only = sourceLemma.online_only
            )

            // Copy lemma_pos entries (filter by posFilter if provided)
            val allLemmaPosEntries = sourceQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
            val lemmaPosEntries = if (posFilter != null) {
                // Convert posFilter strings to DictionaryPos for comparison
                val posEnumFilter = posFilter.mapNotNull { mapPos(it) }.toSet()
                allLemmaPosEntries.filter { lp -> posEnumFilter.contains(lp.pos) }
            } else {
                allLemmaPosEntries
            }
            lemmaPosEntries.forEach { lp ->
                targetQ.insertLemmaPos(
                    id = lp.id,
                    lemma_id = lemmaId,
                    pos = lp.pos
                )

                // Copy forms for this lemma_pos
                val forms = sourceQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList()
                forms.forEach { form ->
                    targetQ.insertForm(
                        form_id = form.form_id,
                        lemma_pos_id = lp.id,
                        form = form.form,
                        form_normalized = stripAccents(form.form),
                        source = form.source
                    )

                    // Copy form tags
                    val tags = sourceQ.selectFormTagsByFormId(form.form_id).executeAsList()
                    tags.forEach { tagRow ->
                        targetQ.insertFormTag(form_id = form.form_id, tag = tagRow.tag)
                    }
                }

                // Copy sense routing hints for this lemma_pos (pos-filter scoped by loop)
                val hints = sourceQ.selectLemmaPosHintsByLemmaPosId(lp.id).executeAsList()
                hints.forEach { hint ->
                    targetQ.insertLemmaPosHint(hint.sense_id, hint.lemma_pos_id)
                }
            }
        }
    }

    private fun mapPos(pos: String): DictionaryPos? {
        try {
            return DictionaryPos.valueOf(pos.uppercase())
        } catch (_: IllegalArgumentException) {
            when (pos.uppercase()) {
                "ADJ" -> return DictionaryPos.ADJECTIVE
                "ADV" -> return DictionaryPos.ADVERB
                "PREP" -> return DictionaryPos.PREPOSITION
                "CONJ" -> return DictionaryPos.CONJUNCTION
                "PRON" -> return DictionaryPos.PRONOUN
                "INTJ" -> return DictionaryPos.INTERJECTION
                "NUM" -> return DictionaryPos.NUMERAL
                "DET" -> return DictionaryPos.DETERMINER
            }
            return null
        }
    }

    private fun mapLevel(level: String): LearnerLevel = LearnerLevel.valueOf(level.uppercase())
    private fun mapFrequency(freq: String): SenseFrequency = when (freq.uppercase()) {
        "HIGH" -> SenseFrequency.HIGH
        "MIDDLE" -> SenseFrequency.MIDDLE
        "LOW" -> SenseFrequency.LOW
        "VERYLOW" -> SenseFrequency.VERY_LOW
        else -> throw IllegalArgumentException("Unknown sense frequency: $freq")
    }

    private fun mapNameType(name: String?): NameType? {
        if (name == null) return null
        return NameType.valueOf(name.trim().uppercase())
    }

    private fun mapTraitType(t: TraitType): DictTraitType = DictTraitType.valueOf(t.name)


    private fun collectTargetLanguages(p: LanguageCardResponse): Set<String> {
        val set = mutableSetOf<String>()
        p.entries.forEach { e ->
            e.senses.forEach { s ->
                set += s.targetLangDefinitions.keys
                set += s.translations.keys
                s.examples.forEach { ex -> set += ex.targetLangTranslations.keys }
            }
        }
        return set
    }

    private fun buildLemmaPosIdToForms(
        entries: List<EntryWithSource>,
        entryIdToLemmaPosId: Map<Uuid, Uuid>
    ): Map<Uuid, List<Pair<ExtractedWordForm, FormSource>>> {
        val lemmaPosIdToForms =
            mutableMapOf<Uuid, MutableMap<FormKey, Pair<ExtractedWordForm, FormSource>>>()
        entries.forEach { (entry, source) ->
            val entryId = uuidParse(entry.entryId.toString())
            val lemmaPosId = entryIdToLemmaPosId[entryId] ?: return@forEach

            val formsMap = lemmaPosIdToForms.getOrPut(lemmaPosId) { mutableMapOf() }
            entry.forms.forEach { f ->
                val key = FormKey(f.form, stripAccents(f.form), f.tags.toSet(), source)
                if (!formsMap.containsKey(key)) {
                    formsMap[key] = Pair(f, source)
                }
            }
        }
        return lemmaPosIdToForms.mapValues { (_, formsMap) -> formsMap.values.toList() }
    }

    private fun insertForms(
        dictQ: DictionaryQueries,
        lemmaPosIdToForms: Map<Uuid, List<Pair<ExtractedWordForm, FormSource>>>
    ) {
        lemmaPosIdToForms.forEach { (lemmaPosId, forms) ->
            forms.forEach { (f, source) ->
                val formId = uuidParse(f.formId.toString())
                dictQ.insertForm(
                    form_id = formId,
                    lemma_pos_id = lemmaPosId,
                    form = f.form,
                    form_normalized = stripAccents(f.form),
                    source = source
                )
                f.tags.forEach { tag ->
                    dictQ.insertFormTag(form_id = formId, tag = tag)
                }
            }
        }
    }

    companion object {
        /**
         * Generates a deterministic lemma ID from the lemma and its normalized form.
         * Uses MD5 hash to ensure consistent IDs across platforms.
         */
        fun generateLemmaId(lemma: String, lemmaNormalized: String): Uuid {
            val hash = md5("${lemma}_${lemmaNormalized}".encodeToByteArray())
            return Uuid.fromByteArray(hash.sliceArray(0..15))
        }

        /**
         * Generates a deterministic lemma ID from just the lemma.
         * Automatically normalizes using stripAccents.
         */
        fun generateLemmaId(lemma: String): Uuid =
            generateLemmaId(lemma, stripAccents(lemma))
    }
}

/**
 * Parses a UUID string, with fallback handling for incomplete UUIDs.
 *
 * If the standard parse fails, attempts to pad the hex digits to 32 characters.
 *
 * @param string The UUID string to parse
 * @return The parsed Uuid
 * @throws IllegalArgumentException if the string cannot be parsed as a valid UUID
 */
fun uuidParse(string: String): Uuid = try {
    Uuid.parse(string)
} catch (_: IllegalArgumentException) {
    // Pad incomplete UUID with zeros to reach required length
    val paddedId = string.replace(Regex("[^abcdef0-9]"), "")
        .padEnd(32, '0').substring(0, 32)
    try {
        Uuid.parse(paddedId)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid UUID: $string", e)
    }
}

@file:OptIn(ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.LearnerLevel
import com.slovy.slovymovyapp.data.dictionary.NameType
import com.slovy.slovymovyapp.data.dictionary.SenseFrequency
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryQueries
import com.slovy.slovymovyapp.translation.TranslationDatabase
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
 */
class JsonIngestionBuilder(
    private val translationDbProvider: (from: String, to: String) -> TranslationDatabase,
    private val frequencyMap: Map<String, Double>
) {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

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
        val raw = json.decodeFromString(ExtractedWordData.serializer(), rawJson)
        val langCode = raw.langCode
        val lemmaWord = raw.word
        val dictQ: DictionaryQueries = dictDb.dictionaryQueries

        val zipfFrequency = frequencyMap[lemmaWord]
            ?: throw IllegalArgumentException("Lemma '$lemmaWord' not found in frequency map")

        val lemmaNormalized = stripAccents(lemmaWord)

        dictDb.transaction {
            val nativeKey = LANG_TO_SOURCE_FILE[langCode]
            val nativeEntries = nativeKey?.let { raw.sourceFileToEntries[it] }.orEmpty()
            val allEntries = raw.sourceFileToEntries.values.flatten()
            val entriesForForms = if (nativeEntries.any { it.forms.isNotEmpty() }) nativeEntries else allEntries

            val baseLemmaId = generateLemmaId(lemmaWord, lemmaNormalized)

            val selectLemmasById = dictQ.selectLemmasById(baseLemmaId).executeAsOneOrNull()
            if (selectLemmasById != null) {
                throw IllegalArgumentException("Lemma '$lemmaWord' already exists in database")
            }

            dictQ.insertLemma(
                id = baseLemmaId,
                lemma = lemmaWord,
                lemma_normalized = lemmaNormalized,
                zipf_frequency = zipfFrequency,
                online_only = true
            )

            val posToEntryId = mutableMapOf<DictionaryPos, Uuid>()
            entriesForForms.forEach { entry ->
                val pos = mapPos(entry.pos) ?: return@forEach
                if (!posToEntryId.containsKey(pos))
                    posToEntryId[pos] = uuidParse(entry.entryId.toString())
            }

            posToEntryId.forEach { (pos, entryId) ->
                dictQ.insertLemmaPos(
                    id = entryId,
                    lemma_id = baseLemmaId,
                    pos = pos
                )
            }

            data class FormKey(val form: String, val formNormalized: String, val tags: Set<String>)

            val lemmaPosIdToForms = mutableMapOf<Uuid, MutableMap<FormKey, ExtractedWordForm>>()
            entriesForForms.forEach { entry ->
                val pos = mapPos(entry.pos) ?: return@forEach
                val lemmaPosId = posToEntryId[pos] ?: return@forEach

                val formsMap = lemmaPosIdToForms.getOrPut(lemmaPosId) { mutableMapOf() }
                entry.forms.forEach { f ->
                    val key = FormKey(f.form, stripAccents(f.form), f.tags.toSet())
                    if (!formsMap.containsKey(key)) {
                        formsMap[key] = f
                    }
                }
            }

            lemmaPosIdToForms.forEach { (lemmaPosId, formsMap) ->
                formsMap.values.forEach { f ->
                    val formId = uuidParse(f.formId.toString())
                    dictQ.insertForm(
                        form_id = formId,
                        lemma_pos_id = lemmaPosId,
                        form = f.form,
                        form_normalized = stripAccents(f.form),
                    )
                    f.tags.forEach { tag ->
                        dictQ.insertFormTag(form_id = formId, tag = tag)
                    }
                }
            }
        }
    }

    /**
     * Ingests processed and raw JSON data into the dictionary and translation databases.
     *
     * @param processedJson JSON string containing the processed LanguageCardResponse
     * @param rawJson JSON string containing the raw ExtractedWordData
     * @throws IllegalArgumentException if lemma not found in frequency map, duplicate IDs, or lemma already exists
     */
    fun ingest(
        processedJson: String, rawJson: String, dictDb: DictionaryDatabase
    ) {
        val processed = json.decodeFromString(LanguageCardResponse.serializer(), processedJson)
        val raw = json.decodeFromString(ExtractedWordData.serializer(), rawJson)
        val langCode = raw.langCode
        val lemmaWord = raw.word
        val dictQ: DictionaryQueries = dictDb.dictionaryQueries

        val zipfFrequency = frequencyMap[lemmaWord]
            ?: throw IllegalArgumentException("Lemma '$lemmaWord' not found in frequency map")

        val lemmaNormalized = stripAccents(lemmaWord)
        dictDb.transaction {

            // Select native source entries; fallback to any when missing
            val nativeKey = LANG_TO_SOURCE_FILE[langCode]
            val nativeEntries = nativeKey?.let { raw.sourceFileToEntries[it] }.orEmpty()
            val allEntries = raw.sourceFileToEntries.values.flatten()

            // Build mapping from sense_id -> raw entry
            val senseIdToRawEntry = mutableMapOf<Uuid, ExtractedWordEntry>()
            allEntries.forEach { entry ->
                entry.senses.forEach { s ->
                    val sid = uuidParse(s.senseId.toString())
                    if (senseIdToRawEntry.contains(sid)) {
                        throw IllegalArgumentException("Duplicate sense_id: $sid")
                    }
                    senseIdToRawEntry[sid] = entry
                }
            }

            val posToEntryId = mutableMapOf<DictionaryPos, Uuid>()
            val entryIdToPos = mutableMapOf<Uuid, DictionaryPos>()
            processed.entries.forEach { pEntry ->
                val pPos = mapPos(pEntry.pos)!!
                pEntry.senses.forEach { s ->
                    val sid = uuidParse(s.senseId)
                    val rawEntry = senseIdToRawEntry[sid]
                    if (rawEntry != null && mapPos(rawEntry.pos) == pPos) {
                        if (!posToEntryId.containsKey(pPos)) {
                            posToEntryId[pPos] = uuidParse(rawEntry.entryId.toString())
                        }
                        entryIdToPos[uuidParse(rawEntry.entryId.toString())] = pPos
                        return@forEach
                    }
                }
            }

            processed.entries.forEach { entry ->
                val key = mapPos(entry.pos)
                if (!posToEntryId.containsKey(key)) {
                    val hash = md5("${lemmaWord}_${lemmaNormalized}_${key!!.name}".encodeToByteArray())
                    posToEntryId[key] = Uuid.fromByteArray(hash.sliceArray(0..15))
                }
            }

            // Create single lemma entry (shared across all POS)
            // Deterministic lemma ID generation using MD5 hash
            val baseLemmaId = generateLemmaId(lemmaWord, lemmaNormalized)

            val selectLemmasById = dictQ.selectLemmasById(baseLemmaId).executeAsOneOrNull()
            if (selectLemmasById != null) {
                throw IllegalArgumentException("Lemma '$lemmaWord' already exists in database")
            }

            dictQ.insertLemma(
                id = baseLemmaId,
                lemma = lemmaWord,
                lemma_normalized = lemmaNormalized,
                zipf_frequency = zipfFrequency,
                online_only = false
            )

            // Insert word family
            processed.wordFamily?.forEach { familyWord ->
                dictQ.insertLemmaWordFamily(lemma_id = baseLemmaId, word = familyWord)
            }

            // Insert lemma_pos entries for all POSes
            posToEntryId.forEach { (pos, entryId) ->
                try {
                    dictQ.insertLemmaPos(
                        id = entryId,
                        lemma_id = baseLemmaId,
                        pos = pos
                    )
                } catch (e: Exception) {
                    throw IllegalArgumentException("Duplicate lemma_pos entry for lemma '$lemmaWord' and POS '$pos'", e)
                }
            }

            // Insert forms (prefer native source; fallback to others when no forms in native)
            val entriesForForms = if (nativeEntries.any { it.forms.isNotEmpty() }) nativeEntries else allEntries

            // Group forms by lemma_pos_id and deduplicate
            data class FormKey(val form: String, val formNormalized: String, val tags: Set<String>)

            val lemmaPosIdToForms = mutableMapOf<Uuid, MutableMap<FormKey, ExtractedWordForm>>()

            entriesForForms.forEach { entry ->
                val pos = entryIdToPos[uuidParse(entry.entryId.toString())]
                pos ?: return@forEach
                val lemmaPosId = posToEntryId[pos] ?: return@forEach

                val formsMap = lemmaPosIdToForms.getOrPut(lemmaPosId) { mutableMapOf() }
                entry.forms.forEach { f ->
                    val key = FormKey(f.form, stripAccents(f.form), f.tags.toSet())
                    // Keep first occurrence of each unique form
                    if (!formsMap.containsKey(key)) {
                        formsMap[key] = f
                    }
                }
            }

            // Insert deduplicated forms
            lemmaPosIdToForms.forEach { (lemmaPosId, formsMap) ->
                formsMap.values.forEach { f ->
                    val formId = uuidParse(f.formId.toString())
                    dictQ.insertForm(
                        form_id = formId,
                        lemma_pos_id = lemmaPosId,
                        form = f.form,
                        form_normalized = stripAccents(f.form),
                    )
                    // tags
                    f.tags.forEach { tag ->
                        dictQ.insertFormTag(form_id = formId, tag = tag)
                    }
                }
            }

            // Insert senses and related data from processed JSON, mapped to POS lemma
            processed.entries.forEach { posEntry ->
                val pos = mapPos(posEntry.pos)
                val lemmaPosIdForPos = posToEntryId[pos]!!

                posEntry.senses.forEachIndexed { _, sense ->
                    val senseId = uuidParse(sense.senseId)
                    dictQ.insertSense(
                        sense_id = senseId,
                        lemma_pos_id = lemmaPosIdForPos,
                        sense_definition = sense.senseDefinition,
                        learner_level = mapLevel(sense.learnerLevel),
                        frequency = mapFrequency(sense.frequency),
                        semantic_group_id = sense.semanticGroupId,
                        name_type = mapNameType(sense.nameType)
                    )
                    // traits
                    sense.traits.forEach { t ->
                        dictQ.insertSenseTrait(
                            sense_id = senseId,
                            trait_type = mapTraitType(t.traitType),
                            comment = t.comment
                        )
                    }
                    // synonyms
                    sense.synonyms.forEach { syn ->
                        dictQ.insertSenseSynonym(sense_id = senseId, synonym = syn)
                    }
                    // antonyms
                    sense.antonyms.forEach { ant ->
                        dictQ.insertSenseAntonym(sense_id = senseId, antonym = ant)
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

            // Build translation DBs per target language encountered
            val targetLangs = collectTargetLanguages(processed)
            targetLangs.forEach { trg ->
                val trDb = translationDbProvider(raw.langCode, trg)
                val trQ = trDb.translationQueries
                trDb.transaction {
                    processed.entries.forEach { posEntry ->
                        val pos = mapPos(posEntry.pos)
                        val lemmaPosIdForPos = posToEntryId[pos]!!
                        posEntry.senses.forEach { sense ->
                            val senseId = uuidParse(sense.senseId)
                            // definitions
                            val def = sense.targetLangDefinitions[trg]
                            if (def != null) {
                                trQ.insertSenseTargetDefinition(sense_id = senseId, definition = def)
                            }
                            // translations list preserving order
                            val translations = sense.translations[trg] ?: emptyList()
                            translations.forEachIndexed { idx, t ->
                                trQ.insertSenseTranslation(
                                    senseId,
                                    idx.toLong(),
                                    t.targetLangWord,
                                    stripAccents(t.targetLangWord),
                                    t.targetLangSenseClarification,
                                    baseLemmaId,
                                    lemmaPosIdForPos
                                )
                            }
                            // example translations by index
                            sense.examples.forEachIndexed { exIdx, ex ->
                                val exTr = ex.targetLangTranslations[trg]
                                if (exTr != null) {
                                    trQ.insertExampleTranslation(
                                        sense_id = senseId,
                                        example_id = exIdx.toLong(),
                                        translation = exTr
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun generateLemmaId(lemma: String, lemmaNormalized: String): Uuid {
        val hash = md5("${lemma}_${lemmaNormalized}".encodeToByteArray())
        return Uuid.fromByteArray(hash.sliceArray(0..15))
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

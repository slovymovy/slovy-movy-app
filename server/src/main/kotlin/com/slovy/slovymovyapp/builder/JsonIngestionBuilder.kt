@file:OptIn(ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.LearnerLevel
import com.slovy.slovymovyapp.data.dictionary.NameType
import com.slovy.slovymovyapp.data.dictionary.SenseFrequency
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.security.MessageDigest
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

class JsonIngestionBuilder(
    private val serverDbManager: ServerDbManager,
    private val frequencyMap: Map<String, Double>
) {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }


    fun ingest(processedFile: File, rawFile: File) {
        val processed = json.decodeFromString(LanguageCardResponse.serializer(), processedFile.readText())
        val raw = json.decodeFromString(ExtractedWordData.serializer(), rawFile.readText())
        val langCode = raw.langCode
        val lemmaWord = raw.word

        val zipfFrequency = frequencyMap[lemmaWord]
            ?: throw IllegalArgumentException("Lemma '$lemmaWord' not found in frequency map")

        val dictDb = serverDbManager.openDictionary(langCode)
        val dictQ = dictDb.dictionaryQueries
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
                        posToEntryId.putIfAbsent(pPos, uuidParse(rawEntry.entryId.toString()))
                        entryIdToPos[uuidParse(rawEntry.entryId.toString())] = pPos
                        return@forEach
                    }
                }
            }

            processed.entries.forEach { entry ->
                val key = mapPos(entry.pos)
                if (!posToEntryId.containsKey(key)) {
                    posToEntryId[key!!] = Uuid.random() // TODO: make it consistent
                }
            }

            // Create single lemma entry (shared across all POS)
            val md = MessageDigest.getInstance("MD5")
            val lemmaNormalized = unaccent(lemmaWord)
            val str = lemmaWord + "_" + lemmaNormalized

            val hash = md.digest(str.toByteArray(Charsets.UTF_8))
            val baseLemmaId = Uuid.fromByteArray(hash.sliceArray(0..15))

            val selectLemmasById = dictQ.selectLemmasById(baseLemmaId).executeAsOneOrNull()
            if (selectLemmasById != null) {
                throw IllegalArgumentException("Lemma '$lemmaWord' already exists in database")
            }

            dictQ.insertLemma(
                id = baseLemmaId,
                lemma = lemmaWord,
                lemma_normalized = lemmaNormalized,
                zipf_frequency = zipfFrequency
            )

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
            entriesForForms.forEach { entry ->
                val pos = entryIdToPos[uuidParse(entry.entryId.toString())]
                pos ?: return@forEach
                val lemmaPosId = posToEntryId[pos] ?: return@forEach

                entry.forms.forEach { f ->
                    val formId = uuidParse(f.formId.toString())
                    dictQ.insertForm(
                        form_id = formId,
                        lemma_pos_id = lemmaPosId,
                        form = f.form,
                        form_normalized = unaccent(f.form),
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

                posEntry.senses.forEachIndexed { sIdx, sense ->
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
                val trDb = serverDbManager.openTranslation(raw.langCode, trg)
                val trQ = trDb.translationQueries
                trDb.transaction {
                    processed.entries.forEach { posEntry ->
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
                                    sense_id = senseId,
                                    idx = idx.toLong(),
                                    target_lang_word = t.targetLangWord,
                                    target_lang_sense_clarification = t.targetLangSenseClarification
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

    private fun mapPos(pos: String): DictionaryPos? {
        try {
            return DictionaryPos.valueOf(pos.uppercase())
        } catch (e: IllegalArgumentException) {
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

    private fun mapLevel(level: String): LearnerLevel = LearnerLevel.valueOf(level.uppercase());
    private fun mapFrequency(freq: String): SenseFrequency = when (freq.uppercase()) {
        "HIGH" -> SenseFrequency.HIGH
        "MIDDLE" -> SenseFrequency.MIDDLE
        "LOW" -> SenseFrequency.LOW
        "VERYLOW" -> SenseFrequency.VERY_LOW
        else -> throw IllegalArgumentException("Unknown sense frequency: $freq")
    }

    private fun mapNameType(name: String?): NameType? {
        if (name == null) return null
        return NameType.valueOf(name.trim().uppercase());
    }

    private fun mapTraitType(t: com.slovy.slovymovyapp.builder.TraitType): DictTraitType = DictTraitType.valueOf(t.name)


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


fun unaccent(s: String): String {
    // Normalize to lowercase first, then handle specific Latin ligatures/letters
    val lower = s.lowercase()
    val replaced = lower
        .replace("æ", "ae")
        .replace("œ", "oe")
        .replace("ø", "o")
        .replace("ł", "l")
    // Strip remaining accents/diacritics (does not affect Cyrillic)
    return StringUtils.stripAccents(replaced)
}
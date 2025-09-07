@file:UseSerializers(UUIDSerializer::class)

package com.slovy.slovymovyapp.builder

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

/**
 * Data classes for extracted Wiktionary data with database IDs included.
 * These models represent the extracted and processed data ready for output or further processing.
 */

/**
 * Complete extracted word data with all related information
 */
@Serializable
data class ExtractedWordData(
    val word: String,
    @SerialName("lang_code") val langCode: String,
    @SerialName("source_file_to_entries") val sourceFileToEntries: Map<String, List<ExtractedWordEntry>>
)

/**
 * An individual word entry with all its components
 */
@Serializable
data class ExtractedWordEntry(
    @SerialName("entry_id") val entryId: UUID,
    val word: String,
    val pos: String,
    @SerialName("lang_code") val langCode: String,
    val forms: MutableList<ExtractedWordForm>,
    val senses: MutableList<ExtractedWordSense>,
    val translations: MutableList<ExtractedTranslation>,
    @SerialName("word_linkages") val wordLinkages: MutableList<ExtractedWordLinkage>,
)

/**
 * Word form with database ID
 */
@Serializable
data class ExtractedWordForm(
    @SerialName("form_id") val formId: UUID,
    @SerialName("entry_id") val entryId: UUID,
    val tags: MutableList<String>,
    val form: String,
    val note: String? = null
)

/**
 * Word sense with database ID and related data
 */
@Serializable
data class ExtractedWordSense(
    @SerialName("sense_id") val senseId: UUID,
    @SerialName("entry_id") val entryId: UUID,
    val glosses: MutableList<String>,
    val tags: MutableList<String>,
    val note: String? = null,
    val examples: MutableList<ExtractedExample>,
    @SerialName("sense_index_json") val senseIndexJson: String? = null
)

/**
 * Example with database ID
 */
@Serializable
data class ExtractedExample(
    @SerialName("example_id") val exampleId: UUID,
    @SerialName("sense_id") val senseId: UUID,
    val text: String,
    val translation: String? = null,
    val english: String? = null,
    val note: String? = null
)

/**
 * Translation with database ID and cross-reference information
 */
@Serializable
data class ExtractedTranslation(
    @SerialName("translation_id") val translationId: UUID,
    @SerialName("source_entry_id") val sourceEntryId: UUID,
    @SerialName("source_sense_id") var sourceSenseId: UUID? = null,
    @SerialName("source_sense_description") val sourceSenseDescription: String? = null,
    @SerialName("target_lang_word") val targetLangWord: String,
    @SerialName("target_lang_code") val targetLangCode: String,
    val note: String? = null,

    @SerialName("target_sense_ids") var targetSenseIds: MutableList<UUID>,
    @SerialName("cross_references") val crossReferences: MutableList<ExtractedCrossReference>
)

/**
 * Word linkage (synonym, antonym, etc.) with database ID
 */
@Serializable
data class ExtractedWordLinkage(
    @SerialName("linkage_id") val linkageId: UUID,
    @SerialName("source_entry_id") val sourceEntryId: UUID,
    @SerialName("source_sense_ids") var sourceSenseIds: MutableList<UUID>,
    @SerialName("source_sense_description") val sourceSenseDescription: String? = null,
    @SerialName("linkage_type") val linkageType: String,
    val word: String,
    val note: String? = null
)

/**
 * Cross-reference to other dictionary entries for translations
 */
@Serializable
data class ExtractedCrossReference(
    @SerialName("target_word") val targetWord: String,
    @SerialName("target_lang_code") val targetLangCode: String,
    @SerialName("target_senses") val targetSenses: MutableList<ExtractedWordSense>
)

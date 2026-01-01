package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.ingestion.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.uuid.ExperimentalUuidApi

/**
 * Utilities for turning db_extract fixtures into AI enhancer requests.
 * Kept in the main module so both production code and tests can share the same conversions.
 */
@OptIn(ExperimentalUuidApi::class)
object DbExtractEnhancerUtils {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadExtractedWordData(
        word: String,
        langCode: String,
        resourceRoot: String = "db_extract"
    ): ExtractedWordData {
        val path = "$resourceRoot/$langCode/$word.json"
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        val file = File(url.toURI())
        return json.decodeFromString(ExtractedWordData.serializer(), file.readText())
    }

    fun createLanguageCardRequest(extractedData: ExtractedWordData): LanguageCardRequest? {
        val sourceFiles = mutableSetOf<String>()
        LANG_TO_SOURCE_FILE[extractedData.langCode]?.let { sourceFiles.add(it) }
        LANG_TO_SOURCE_FILE["en"]?.let { sourceFiles.add(it) }

        val entries = extractedData.sourceFileToEntries
            .filter { it.key in sourceFiles }
            .values
            .flatten()
            .ifEmpty { extractedData.sourceFileToEntries.values.flatten() }

        if (entries.isEmpty()) return null

        return LanguageCardRequest(
            word = extractedData.word,
            langCode = extractedData.langCode,
            entries = entries.map { it.toRequestEntry() }
        )
    }

    fun createLanguageCardRequest(
        word: String,
        langCode: String,
        resourceRoot: String = "db_extract"
    ): LanguageCardRequest? {
        return createLanguageCardRequest(loadExtractedWordData(word, langCode, resourceRoot))
    }

    fun createTranslationRequest(
        word: String,
        langCode: String,
        targetLangCode: String,
        languageCardData: LanguageCardResponse,
        resourceRoot: String = "db_extract"
    ): TranslationRequest {
        val extractedData = loadExtractedWordData(word, langCode, resourceRoot)
        val targetTranslations = extractedData.sourceFileToEntries.values
            .flatten()
            .flatMap { it.translations }
            .filter { it.targetLangCode == targetLangCode }

        return TranslationRequest(
            word = word,
            langCode = langCode,
            targetLangCode = targetLangCode,
            languageCardData = languageCardData,
            translations = targetTranslations
        )
    }

    fun targetLanguageName(langCode: String): String {
        return when (langCode) {
            "en" -> "English"
            "nl" -> "Dutch"
            "ru" -> "Russian"
            "pl" -> "Polish"
            "fr" -> "French"
            "it" -> "Italian"
            "de" -> "German"
            "cs" -> "Czech"
            "es" -> "Spanish"
            "tr" -> "Turkish"
            else -> error("Unsupported language code: $langCode")
        }
    }

    private fun ExtractedWordEntry.toRequestEntry(): LanguageCardEntry {
        val formReqs = forms.map { it.toRequestForm() }
        val senseReqs = senses.map { it.toRequestSense() }
        val linkReqs = wordLinkages.map { it.toRequestLinkage() }
        return LanguageCardEntry(
            pos = pos,
            forms = formReqs.ifEmpty { emptyList() },
            senses = senseReqs,
            wordLinkages = linkReqs.ifEmpty { emptyList() }
        )
    }

    private fun ExtractedWordForm.toRequestForm(): LanguageCardForm {
        return LanguageCardForm(
            form = form,
            tags = if (tags.isEmpty()) null else tags.toList(),
            note = note
        )
    }

    private fun ExtractedWordSense.toRequestSense(): LanguageCardSense {
        return LanguageCardSense(
            senseId = senseId.toString(),
            glosses = glosses.toList(),
            examples = examples.map { LanguageCardExample(it.text) }
        )
    }

    private fun ExtractedWordLinkage.toRequestLinkage(): LanguageCardWordLinkage {
        val linkageType = LinkageType.valueOf(linkageType.uppercase().replace(" ", "_"))
        return LanguageCardWordLinkage(
            word = word,
            linkageType = linkageType,
            sourceSenseDescription = sourceSenseDescription
        )
    }
}

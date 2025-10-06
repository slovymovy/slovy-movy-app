package com.slovy.slovymovyapp.db

import app.cash.sqldelight.db.use
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.dictionary.*
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.uuid.Uuid


class DatabaseTypesTest : BaseTest() {

    data class DictionaryOutcome(
        val lemmaId: Uuid,
        val lemma: String,
        val lemmaNormalized: String,
        val lemmasByWordCount: Int,
        val formsByNormalizedCount: Int,
        val lemmaFoundByNormalized: Boolean,
        val senseId: Uuid,
    )

    data class TranslationOutcome(
        val definitions: List<String>,
        val translationWordsInOrder: List<String>,
        val translationClarificationsInOrder: List<String?>,
        val exampleTranslation: String?,
    )

    @Test
    fun dictionary_types_round_trip() {
        val path = Path(Uuid.random().toString() + "dictionary_types.db")
        try {
            val driver = platformDbSupport()
                .createDictionaryDataDriver(path, false)
            driver.use {
                val db: DictionaryDatabase = DatabaseProvider.createDictionaryDatabase(driver)
                val q = db.dictionaryQueries
                val lemmaId = Uuid.random()
                val lemma = "Test"
                val lemmaNormalized = "test"
                q.insertPosEntry(
                    id = lemmaId,
                    lemma = lemma,
                    lemma_normalized = lemmaNormalized,
                    pos = DictionaryPos.VERB,
                    zipf_frequency = 0.2,
                )
                val lemmasByWord = q.selectLemmasByWord(lemma.lowercase()).executeAsList()
                val formText = "Testing"
                val formNormalized = "testing"
                q.insertForm(
                    form_id = Uuid.random(),
                    lemma_id = lemmaId,
                    form = formText,
                    form_normalized = formNormalized,
                )
                val formsByNorm = q.selectFormsByNormalized(formNormalized).executeAsList()
                val senseId = Uuid.random()
                q.insertSense(
                    sense_id = senseId,
                    lemma_id = lemmaId,
                    sense_definition = "to test; to try",
                    learner_level = LearnerLevel.B1,
                    frequency = SenseFrequency.MIDDLE,
                    semantic_group_id = "sg1",
                    name_type = NameType.NO,
                )
                q.insertSenseTrait(sense_id = senseId, trait_type = TraitType.SLANG, comment = "colloquial usage")
                q.insertSenseSynonym(sense_id = senseId, synonym = "probe")
                q.insertSenseAntonym(sense_id = senseId, antonym = "ignore")
                q.insertSenseExample(sense_id = senseId, example_id = 1, text = "Ми тестуємо систему.")
                q.insertSenseCommonPhrase(sense_id = senseId, phrase = "тестувати воду")
                val lemmasByNorm = q.selectLemmasByNormalized(lemmaNormalized).executeAsList()
                val lemmaFoundByNormalized = lemmasByNorm.any { it.id == lemmaId }
                val out = DictionaryOutcome(
                    lemmaId = lemmaId,
                    lemma = lemma,
                    lemmaNormalized = lemmaNormalized,
                    lemmasByWordCount = lemmasByWord.size,
                    formsByNormalizedCount = formsByNorm.size,
                    lemmaFoundByNormalized = lemmaFoundByNormalized,
                    senseId = senseId,
                )
                require(out.lemmasByWordCount >= 1) {
                    "Should find lemma by case-insensitive word; got count=${out.lemmasByWordCount} for lemma='${out.lemma}'"
                }
                require(out.formsByNormalizedCount >= 1) {
                    "Should find form by normalized string; got count=${out.formsByNormalizedCount} (lemmaId=${out.lemmaId})"
                }
                require(out.lemmaFoundByNormalized) {
                    "Should find lemma by normalized string; lemmaId=${out.lemmaId}, lemmaNormalized='${out.lemmaNormalized}'"
                }
            }
        } finally {
            platformDbSupport().deleteFile(path)
        }
    }

    @Test
    fun translation_types_round_trip() {
        val path = Path(Uuid.random().toString() + "translation_types.db")
        try {
            val driver = platformDbSupport()
                .createTranslationDataDriver(path, false)
            driver.use {
                val db: TranslationDatabase = DatabaseProvider.createTranslationDatabase(driver)
                val q = db.translationQueries
                val senseId = Uuid.random()
                q.insertSenseTargetDefinition(sense_id = senseId, definition = "target definition")
                q.insertSenseTranslation(
                    sense_id = senseId,
                    idx = 0,
                    target_lang_word = "test",
                    target_lang_sense_clarification = "n."
                )
                q.insertSenseTranslation(
                    sense_id = senseId,
                    idx = 1,
                    target_lang_word = "trial",
                    target_lang_sense_clarification = null
                )
                q.insertExampleTranslation(sense_id = senseId, example_id = 42, translation = "Мы тестируем.")
                val defs = q.selectDefinitionsBySense(senseId).executeAsList()
                val translations = q.selectSenseTranslationsBySense(senseId).executeAsList()
                val words = translations.map { it.target_lang_word }
                val clar = translations.map { it.target_lang_sense_clarification }
                val example = q.selectExampleTranslations(senseId, 42).executeAsOneOrNull()
                val out = TranslationOutcome(
                    definitions = defs,
                    translationWordsInOrder = words,
                    translationClarificationsInOrder = clar,
                    exampleTranslation = example,
                )
                val expectedDefs = listOf("target definition")
                if (out.definitions != expectedDefs) {
                    throw IllegalStateException("Definitions mismatch: expected=$expectedDefs, actual=${out.definitions}")
                }
                val expectedWords = listOf("test", "trial")
                if (out.translationWordsInOrder != expectedWords) {
                    throw IllegalStateException("Translation words mismatch: expected=$expectedWords, actual=${out.translationWordsInOrder}")
                }
                val expectedClar = listOf("n.", null)
                if (out.translationClarificationsInOrder != expectedClar) {
                    throw IllegalStateException("Clarifications mismatch: expected=$expectedClar, actual=${out.translationClarificationsInOrder}")
                }
                val expectedExample = "Мы тестируем."
                if (out.exampleTranslation != expectedExample) {
                    throw IllegalStateException("Example translation mismatch: expected='$expectedExample', actual='${out.exampleTranslation}'")
                }
            }
        } finally {
            platformDbSupport().deleteFile(path)
        }
    }
}
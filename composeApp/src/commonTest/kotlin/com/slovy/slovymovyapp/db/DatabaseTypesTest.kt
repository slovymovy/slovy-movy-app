package com.slovy.slovymovyapp.db

import app.cash.sqldelight.db.use
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.dictionary.*
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.uuid.Uuid


class DatabaseTypesTest : BaseTest() {

    data class DictionaryOutcome(
        val lemmaPosId: Uuid,
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
            val driver = testPlatformDbSupport()
                .createDictionaryDataDriver(path, false)
            driver.use {
                val db: DictionaryDatabase = DatabaseProvider.createDictionaryDatabase(driver)
                val q = db.dictionaryQueries
                val baseLemmaId = Uuid.random()
                val lemmaPosId = Uuid.random()
                val lemma = "Test"
                val lemmaNormalized = "test"
                q.insertLemma(
                    id = baseLemmaId,
                    lang_code = "en",
                    lemma = lemma,
                    lemma_normalized = lemmaNormalized,
                    zipf_frequency = 0.2,
                    online_only = false,
                )
                q.insertLemmaPos(
                    id = lemmaPosId,
                    lemma_id = baseLemmaId,
                    pos = DictionaryPos.VERB
                )
                val lemmasByWord = q.selectLemmasByWord("en", lemma).executeAsList()
                val formText = "Testing"
                val formNormalized = "testing"
                q.insertForm(
                    form_id = Uuid.random(),
                    lemma_pos_id = lemmaPosId,
                    form = formText,
                    form_normalized = formNormalized,
                    source = FormSource.NATIVE,
                )
                val formsByNorm = q.selectFormsByNormalized(formNormalized).executeAsList()
                val senseId = Uuid.random()
                q.insertSense(
                    sense_id = senseId,
                    lemma_pos_id = lemmaPosId,
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
                val lemmasByNorm = q.selectLemmasByNormalized("en", lemmaNormalized).executeAsList()
                val lemmaFoundByNormalized = lemmasByNorm.any { it.id == baseLemmaId }
                val out = DictionaryOutcome(
                    lemmaPosId = lemmaPosId,
                    lemma = lemma,
                    lemmaNormalized = lemmaNormalized,
                    lemmasByWordCount = lemmasByWord.size,
                    formsByNormalizedCount = formsByNorm.size,
                    lemmaFoundByNormalized = lemmaFoundByNormalized,
                    senseId = senseId,
                )
                require(out.lemmasByWordCount >= 1) {
                    "Should find lemma by case-sensitive word; got count=${out.lemmasByWordCount} for lemma='${out.lemma}'"
                }
                require(out.formsByNormalizedCount >= 1) {
                    "Should find form by normalized string; got count=${out.formsByNormalizedCount} (lemmaPosId=${out.lemmaPosId})"
                }
                require(out.lemmaFoundByNormalized) {
                    "Should find lemma by normalized string; lemmaPosId=${out.lemmaPosId}, lemmaNormalized='${out.lemmaNormalized}'"
                }
            }
        } finally {
            testPlatformDbSupport().deleteFile(path)
        }
    }

    @Test
    fun translation_types_round_trip() {
        val path = Path(Uuid.random().toString() + "translation_types.db")
        try {
            val driver = testPlatformDbSupport()
                .createTranslationDataDriver(path, false)
            driver.use {
                val db: TranslationDatabase = DatabaseProvider.createTranslationDatabase(driver)
                val q = db.translationQueries
                val senseId = Uuid.random()
                q.insertSenseTargetDefinition(sense_id = senseId, from_lang_code = "en", target_lang_code = "ru", definition = "target definition")
                val lemmaId = Uuid.random()
                val lemmaPosId = Uuid.random()
                q.insertSenseTranslation(
                    sense_id = senseId,
                    from_lang_code = "en",
                    target_lang_code = "ru",
                    idx = 0,
                    target_lang_word = "test",
                    target_lang_word_normalized = "test",
                    target_lang_sense_clarification = "n.",
                    lemma_id = lemmaId,
                    lemma_pos_id = lemmaPosId,
                )
                q.insertSenseTranslation(
                    sense_id = senseId,
                    from_lang_code = "en",
                    target_lang_code = "ru",
                    idx = 1,
                    target_lang_word = "trial",
                    target_lang_word_normalized = "trial",
                    target_lang_sense_clarification = null,
                    lemma_id = lemmaId,
                    lemma_pos_id = lemmaPosId,
                )
                q.insertExampleTranslation(sense_id = senseId, from_lang_code = "en", target_lang_code = "ru", example_id = 42, translation = "Мы тестируем.")
                val defs = q.selectDefinitionsBySense(senseId, "en", "ru").executeAsList()
                val translations = q.selectSenseTranslationsBySense(senseId, "en", "ru").executeAsList()
                val words = translations.map { it.target_lang_word }
                val clar = translations.map { it.target_lang_sense_clarification }
                val example = q.selectExampleTranslations(senseId, "en", "ru", 42).executeAsOneOrNull()
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
            testPlatformDbSupport().deleteFile(path)
        }
    }
}

package com.slovy.slovymovyapp.dbtest

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.dictionary.*
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlin.uuid.Uuid

/**
 * Centralized, test logic that exercises the Dictionary and Translation
 * databases and returns observable outcomes for platform-specific tests to assert on.
 */
object DbTypesTestLogic {

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

    fun exerciseDictionary(driver: SqlDriver): DictionaryOutcome {
        val db: DictionaryDatabase = DatabaseProvider.createDictionaryDatabase(driver)
        val q = db.dictionaryQueries

        val lemmaId = Uuid.random()
        val lemma = "Test"
        val lemmaNormalized = "test"

        q.insertPosEntry(id = lemmaId, lemma = lemma, lemma_normalized = lemmaNormalized)
        val lemmasByWord = q.selectLemmasByWord(lemma.lowercase()).executeAsList()

        val formText = "Testing"
        val formNormalized = "testing"
        q.insertForm(
            lemma_id = lemmaId,
            lemma_id_ = lemmaId,
            form = formText,
            form_normalized = formNormalized,
            pos = DictionaryPos.VERB,
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
            pos = DictionaryPos.VERB,
        )
        q.insertSenseTrait(sense_id = senseId, trait_type = TraitType.SLANG, comment = "colloquial usage")
        q.insertSenseSynonym(sense_id = senseId, synonym = "probe")
        q.insertSenseAntonym(sense_id = senseId, antonym = "ignore")
        q.insertSenseExample(sense_id = senseId, example_id = 1, text = "Ми тестуємо систему.")
        q.insertSenseCommonPhrase(sense_id = senseId, phrase = "тестувати воду")

        val lemmasByNorm = q.selectLemmasByNormalized(lemmaNormalized).executeAsList()
        val lemmaFoundByNormalized = lemmasByNorm.any { it.id == lemmaId }

        return DictionaryOutcome(
            lemmaId = lemmaId,
            lemma = lemma,
            lemmaNormalized = lemmaNormalized,
            lemmasByWordCount = lemmasByWord.size,
            formsByNormalizedCount = formsByNorm.size,
            lemmaFoundByNormalized = lemmaFoundByNormalized,
            senseId = senseId,
        )
    }

    fun exerciseTranslation(driver: SqlDriver): TranslationOutcome {
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

        return TranslationOutcome(
            definitions = defs,
            translationWordsInOrder = words,
            translationClarificationsInOrder = clar,
            exampleTranslation = example,
        )
    }

    // Common validation helpers (throwing exceptions for platform-agnostic assertions)
    fun validateDictionaryOutcome(o: DictionaryOutcome) {
        require(o.lemmasByWordCount >= 1) {
            "Should find lemma by case-insensitive word; got count=${o.lemmasByWordCount} for lemma='${o.lemma}'"
        }
        require(o.formsByNormalizedCount >= 1) {
            "Should find form by normalized string; got count=${o.formsByNormalizedCount} (lemmaId=${o.lemmaId})"
        }
        require(o.lemmaFoundByNormalized) {
            "Should find lemma by normalized string; lemmaId=${o.lemmaId}, lemmaNormalized='${o.lemmaNormalized}'"
        }
    }

    fun validateTranslationOutcome(o: TranslationOutcome) {
        val expectedDefs = listOf("target definition")
        if (o.definitions != expectedDefs) {
            throw IllegalStateException("Definitions mismatch: expected=$expectedDefs, actual=${o.definitions}")
        }
        val expectedWords = listOf("test", "trial")
        if (o.translationWordsInOrder != expectedWords) {
            throw IllegalStateException("Translation words mismatch: expected=$expectedWords, actual=${o.translationWordsInOrder}")
        }
        val expectedClar = listOf("n.", null)
        if (o.translationClarificationsInOrder != expectedClar) {
            throw IllegalStateException("Clarifications mismatch: expected=$expectedClar, actual=${o.translationClarificationsInOrder}")
        }
        val expectedExample = "Мы тестируем."
        if (o.exampleTranslation != expectedExample) {
            throw IllegalStateException("Example translation mismatch: expected='$expectedExample', actual='${o.exampleTranslation}'")
        }
    }
}

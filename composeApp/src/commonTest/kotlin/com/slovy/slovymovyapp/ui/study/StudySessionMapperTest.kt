package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.Card
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardScheduling
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.learning.session.ExamplePair
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LanguageCardTranslation
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.data.remote.WordResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class StudySessionMapperTest {

    @Test
    fun mapsWordToTranslationRecognitionCard() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = Language.ENGLISH.code),
        )

        val mapped = assertIs<StudyCardUiState.Recognition>(sessionCard.toStudyCardUiState())

        assertEquals("gezellig", mapped.promptWord)
        assertEquals(StudyRecognitionMode.BILINGUAL, mapped.mode)
        assertEquals("cosy, sociable", mapped.back.headline)
        assertEquals("a feeling of warmth", mapped.back.definition)
        assertEquals("Het was zo <w>gezellig</w>.", mapped.back.examples.single().text)
        assertEquals("It was so cosy.", mapped.back.examples.single().translation)
    }

    @Test
    fun mapsListeningTranslationCard() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.LISTENING_TRANSLATION, targetLang = Language.ENGLISH.code),
        )

        val mapped = assertIs<StudyCardUiState.Listening>(sessionCard.toStudyCardUiState())

        assertEquals("gezellig", mapped.promptAudioText)
        assertEquals("gezellig", mapped.back.headline)
        assertEquals("cosy, sociable", mapped.back.secondary)
        assertEquals("a feeling of warmth", mapped.back.definition)
        assertEquals("Het was zo <w>gezellig</w>.", mapped.back.examples.single().text)
        assertEquals("It was so cosy.", mapped.back.examples.single().translation)
    }

    @Test
    fun mapsSourceClozeCard() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.CLOZE_SOURCE, targetLang = null),
            example = ExamplePair(
                exampleIndex = 0,
                text = "Het was zo gezellig.",
                clozeRange = 11..18,
            ),
        )

        val mapped = assertIs<StudyCardUiState.Cloze>(sessionCard.toStudyCardUiState())

        assertEquals("Het was zo ", mapped.prompt.prefix)
        assertEquals("gezellig", mapped.prompt.answer)
        assertEquals(".", mapped.prompt.suffix)
        assertNotNull(mapped.back.cloze)
        assertEquals("gezellig", mapped.back.headline)
    }

    private fun sessionCard(
        variant: CardVariant,
        example: ExamplePair? = null,
    ): SessionCard {
        val senseId = "00000000-0000-0000-0000-000000000101"
        return SessionCard(
            card = Card(
                id = Uuid.parse("00000000-0000-0000-0000-000000000201"),
                senseId = Uuid.parse(senseId),
                lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000301"),
                langCode = Language.DUTCH.code,
                family = variant.kind.family,
                answerKey = "gezellig",
                scheduling = CardScheduling(
                    state = CardState.REVIEW,
                    stability = 1.0,
                    difficulty = 1.0,
                    dueEpochMs = 0L,
                    lastReviewEpochMs = null,
                    reps = 0,
                    lapses = 0,
                    createdAtEpochMs = 0L,
                    availableAfterEpochMs = null,
                    suspended = false,
                ),
            ),
            variant = variant,
            wordResult = WordResult(card = languageCard(senseId)),
            senseId = senseId,
            example = example,
        )
    }

    private fun languageCard(senseId: String): LanguageCard =
        LanguageCard(
            lemma = "gezellig",
            zipfFrequency = 4.2f,
            entries = listOf(
                LanguageCardPosEntry(
                    pos = PartOfSpeech.ADJECTIVE,
                    formsViews = emptyList(),
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = senseId,
                            senseDefinition = "a feeling of warmth",
                            learnerLevel = LearnerLevel.A2,
                            frequency = SenseFrequency.HIGH,
                            semanticGroupId = "warmth",
                            examples = listOf(
                                LanguageCardExample(
                                    text = "Het was zo <w>gezellig</w>.",
                                    targetLangTranslations = mapOf(Language.ENGLISH to "It was so cosy."),
                                ),
                            ),
                            targetLangDefinitions = mapOf(Language.ENGLISH to "a feeling of warmth"),
                            translations = mapOf(
                                Language.ENGLISH to listOf(
                                    LanguageCardTranslation(targetLangWord = "cosy"),
                                    LanguageCardTranslation(targetLangWord = "sociable"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
}

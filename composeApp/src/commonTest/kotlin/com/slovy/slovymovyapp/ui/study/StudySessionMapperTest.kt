package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository.Companion.normalizeLemma
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
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private const val PrimarySenseId = "00000000-0000-0000-0000-000000000101"

class StudySessionMapperTest {

    @Test
    fun mapsWordToTranslationRecognitionCard() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = Language.ENGLISH.code),
        )

        val mapped = assertIs<StudyCardUiState.Recognition>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals("gezellig", mapped.promptWord)
        assertEquals(StudyRecognitionMode.BILINGUAL, mapped.mode)
        assertEquals("cosy, sociable", mapped.back.headline)
        assertEquals("a feeling of warmth", mapped.back.definition)
        assertEquals("een gevoel van warmte", mapped.back.definitionTranslation)
        assertEquals("Het was zo <w>gezellig</w>.", mapped.back.examples.single().text)
        assertEquals("It was so cosy.", mapped.back.examples.single().translation)
    }

    @Test
    fun bilingualRecognitionFiltersSensesToStudiedOnly() {
        val studiedSenseId = "00000000-0000-0000-0000-000000000101"
        val otherSenseId = "00000000-0000-0000-0000-000000000102"
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = Language.ENGLISH.code),
            studiedSenseIds = setOf(studiedSenseId, otherSenseId),
            extraSenses = listOf(
                buildSense(
                    senseId = otherSenseId,
                    definition = "convivial gathering",
                    translation = "sociable evening",
                    exampleText = "Een <w>gezellig</w> avondje.",
                    exampleTranslation = "A cosy evening.",
                ),
                buildSense(
                    senseId = "00000000-0000-0000-0000-000000000103",
                    definition = "not-studied",
                    translation = "not-studied-translation",
                    exampleText = "Niet bestudeerd.",
                    exampleTranslation = "Not studied.",
                ),
            ),
        )

        val mapped = assertIs<StudyCardUiState.Recognition>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals(studiedSenseId, mapped.activeSenseId)
        assertEquals(listOf(studiedSenseId, otherSenseId), mapped.senses.map { it.id })
        assertEquals(listOf(1, 2), mapped.senses.map { it.num })
        val activeSenseBack = mapped.senses.single { it.id == studiedSenseId }.back
        assertEquals(mapped.back.headline, activeSenseBack.headline)
        assertEquals(mapped.back.definition, activeSenseBack.definition)
        assertEquals(
            mapped.back.examples.single().text,
            activeSenseBack.examples.single().text,
        )
        val otherBack = mapped.senses.single { it.id == otherSenseId }.back
        assertEquals("sociable evening", otherBack.headline)
        assertEquals("convivial gathering", otherBack.definition)
        assertEquals("Een <w>gezellig</w> avondje.", otherBack.examples.single().text)
        assertEquals("A cosy evening.", otherBack.examples.single().translation)
    }

    @Test
    fun monolingualRecognitionPopulatesStudiedSenses() {
        val studiedSenseId = "00000000-0000-0000-0000-000000000101"
        val otherSenseId = "00000000-0000-0000-0000-000000000102"
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_SOURCE_DEFINITION, targetLang = null),
            studiedSenseIds = setOf(studiedSenseId, otherSenseId),
            extraSenses = listOf(
                buildSense(
                    senseId = otherSenseId,
                    definition = "convivial gathering",
                    translation = "sociable evening",
                    exampleText = "Een <w>gezellig</w> avondje.",
                    exampleTranslation = "A cosy evening.",
                ),
            ),
        )

        val mapped = assertIs<StudyCardUiState.Recognition>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals(StudyRecognitionMode.MONOLINGUAL, mapped.mode)
        assertEquals(studiedSenseId, mapped.activeSenseId)
        assertEquals(listOf(studiedSenseId, otherSenseId), mapped.senses.map { it.id })
        mapped.senses.forEach { senseUi ->
            assertEquals("gezellig", senseUi.back.headline)
        }
        assertEquals("een gevoel van warmte", mapped.senses.first().back.definition)
        assertEquals("convivial gathering", mapped.senses.last().back.definition)
    }

    @Test
    fun mapsListeningTranslationCard() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.LISTENING_TRANSLATION, targetLang = Language.ENGLISH.code),
        )

        val mapped = assertIs<StudyCardUiState.Listening>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals("gezellig", mapped.promptAudioText)
        assertEquals("gezellig", mapped.back.headline)
        assertEquals("cosy, sociable", mapped.back.secondary)
        assertEquals("een gevoel van warmte", mapped.back.definition)
        assertEquals("a feeling of warmth", mapped.back.definitionTranslation)
        assertEquals("Het was zo <w>gezellig</w>.", mapped.back.examples.single().text)
        assertEquals("It was so cosy.", mapped.back.examples.single().translation)
    }

    @Test
    fun mapsSynonymsWithLibraryAwareness() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = Language.ENGLISH.code),
            synonyms = listOf("knus", "aangenaam", "prettig"),
        )

        val mapped = assertIs<StudyCardUiState.Recognition>(
            sessionCard.toStudyCardUiState(setOf(normalizeLemma("Knus"), normalizeLemma("prettig"))),
        )

        assertEquals(
            listOf(
                StudySynonymUiState("knus", known = true),
                StudySynonymUiState("prettig", known = true),
                StudySynonymUiState("aangenaam", known = false),
            ),
            mapped.back.synonyms,
        )
    }

    @Test
    fun listeningCardPopulatesStudiedSenses() {
        val studiedSenseId = "00000000-0000-0000-0000-000000000101"
        val otherSenseId = "00000000-0000-0000-0000-000000000102"
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.LISTENING_TRANSLATION, targetLang = Language.ENGLISH.code),
            studiedSenseIds = setOf(studiedSenseId, otherSenseId),
            extraSenses = listOf(
                buildSense(
                    senseId = otherSenseId,
                    definition = "convivial gathering",
                    translation = "sociable evening",
                    exampleText = "Een <w>gezellig</w> avondje.",
                    exampleTranslation = "A cosy evening.",
                ),
            ),
        )

        val mapped = assertIs<StudyCardUiState.Listening>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals(studiedSenseId, mapped.activeSenseId)
        assertEquals(listOf(studiedSenseId, otherSenseId), mapped.senses.map { it.id })
        assertEquals(listOf(1, 2), mapped.senses.map { it.num })
        val activeSenseBack = mapped.senses.single { it.id == studiedSenseId }.back
        assertEquals("gezellig", activeSenseBack.headline)
        assertEquals("cosy, sociable", activeSenseBack.secondary)
        assertEquals("een gevoel van warmte", activeSenseBack.definition)
        val otherBack = mapped.senses.single { it.id == otherSenseId }.back
        assertEquals("gezellig", otherBack.headline)
        assertEquals("sociable evening", otherBack.secondary)
        assertEquals("convivial gathering", otherBack.definition)
    }

    @Test
    fun productionCardPopulatesStudiedSensesForBackSide() {
        val studiedSenseId = "00000000-0000-0000-0000-000000000101"
        val otherSenseId = "00000000-0000-0000-0000-000000000102"
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = Language.ENGLISH.code),
            studiedSenseIds = setOf(studiedSenseId, otherSenseId),
            extraSenses = listOf(
                buildSense(
                    senseId = otherSenseId,
                    definition = "convivial gathering",
                    translation = "sociable evening",
                    exampleText = "Een <w>gezellig</w> avondje.",
                    exampleTranslation = "A cosy evening.",
                ),
            ),
        )

        val mapped = assertIs<StudyCardUiState.Production>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals(studiedSenseId, mapped.activeSenseId)
        assertEquals(listOf(studiedSenseId, otherSenseId), mapped.senses.map { it.id })
        assertEquals("gezellig", mapped.senses.first().back.headline)
        assertEquals("cosy, sociable", mapped.senses.first().back.secondary)
        assertEquals("sociable evening", mapped.senses.last().back.secondary)
    }

    @Test
    fun mapsSourceClozeCard() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.CLOZE_SOURCE, targetLang = Language.ENGLISH.code),
            example = ExamplePair(
                exampleIndex = 0,
                text = "Het was zo gezellig.",
                clozeRanges = listOf(11..18),
            ),
        )

        val mapped = assertIs<StudyCardUiState.Cloze>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals("Het was zo gezellig.", mapped.prompt.text)
        assertEquals(listOf(11..18), mapped.prompt.answerRanges)
        // Fixture translation is untagged, so the hint falls back to the first-letter hint.
        assertNull(mapped.translationHint)
        val hint = assertNotNull(mapped.firstLetterHint)
        assertEquals('g', hint.letter)
        assertEquals(8, hint.letterCount)
        assertEquals(7, hint.dotCount)
        assertNotNull(mapped.back.cloze)
        assertEquals("gezellig", mapped.back.headline)
        assertEquals("een gevoel van warmte", mapped.back.definition)
        assertEquals("a feeling of warmth", mapped.back.definitionTranslation)
        val clozeTranslation = assertNotNull(mapped.back.clozeTranslation)
        assertEquals("It was so cosy.", clozeTranslation.text)
        assertEquals(true, clozeTranslation.filled)
    }

    @Test
    fun sourceClozeCardHighlightsTranslationHintWhenTranslationTagged() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.CLOZE_SOURCE, targetLang = Language.ENGLISH.code),
            example = ExamplePair(
                exampleIndex = 0,
                text = "Het was zo gezellig.",
                clozeRanges = listOf(11..18),
            ),
            primaryExampleTranslation = "It was so <w>cosy</w>.",
        )

        val mapped = assertIs<StudyCardUiState.Cloze>(sessionCard.toStudyCardUiState(emptySet()))

        // Tagged translation: highlight the answer word in the translation hint, no first-letter hint.
        assertNull(mapped.firstLetterHint)
        val hint = assertNotNull(mapped.translationHint)
        assertEquals("It was so cosy.", hint.text)
        assertEquals(listOf(10..13), hint.answerRanges)
        assertEquals(true, hint.filled)
    }

    @Test
    fun translationClozeCardExposesFirstLetterHintFromLemma() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.CLOZE_TRANSLATION, targetLang = Language.ENGLISH.code),
            example = ExamplePair(
                exampleIndex = 0,
                text = "It was so cosy.",
                clozeRanges = listOf(10..13),
            ),
            // Inflected form in the source example differs from the lemma; the hint uses the lemma.
            primaryExampleText = "Het was zo <w>gezelliger</w>.",
        )

        val mapped = assertIs<StudyCardUiState.Cloze>(sessionCard.toStudyCardUiState(emptySet()))

        assertNull(mapped.translationHint)
        val hint = assertNotNull(mapped.firstLetterHint)
        assertEquals('g', hint.letter)
        // "gezellig" (lemma, 8) rather than "gezelliger" (inflected form, 10).
        assertEquals(8, hint.letterCount)
        assertEquals(7, hint.dotCount)
    }

    @Test
    fun mapsSourceClozeCardWithMultipleTaggedRanges() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.CLOZE_SOURCE, targetLang = Language.ENGLISH.code),
            example = ExamplePair(
                exampleIndex = 0,
                text = "Vergeet niet om je paspoort mee te nemen.",
                clozeRanges = listOf(28..30, 35..39),
            ),
        )

        val mapped = assertIs<StudyCardUiState.Cloze>(sessionCard.toStudyCardUiState(emptySet()))

        assertEquals("Vergeet niet om je paspoort mee te nemen.", mapped.prompt.text)
        assertEquals(listOf(28..30, 35..39), mapped.prompt.answerRanges)
        // Untagged fixture translation falls back to the first-letter hint of the first answer.
        val hint = assertNotNull(mapped.firstLetterHint)
        assertEquals('m', hint.letter)
        assertEquals(3, hint.letterCount)
        assertEquals(2, hint.dotCount)
        assertNotNull(mapped.back.cloze)
        assertEquals(listOf(28..30, 35..39), mapped.back.cloze.answerRanges)
    }

    @Test
    fun frontSideReplacesEveryAnswerRangeWithBlank() {
        val state = StudyClozeTextUiState(
            text = "Vergeet niet om je paspoort mee te nemen.",
            answerRanges = listOf(28..30, 35..39),
        )

        val display = state.toDisplayText()

        assertEquals("Vergeet niet om je paspoort \u00A0\u00A0\u00A0\u00A0\u00A0\u00A0 te \u00A0\u00A0\u00A0\u00A0\u00A0\u00A0.", display.text)
        assertEquals(listOf(28..33, 38..43), display.answerRanges)
    }

    @Test
    fun backSideHighlightsEveryAnswerRange() {
        val state = StudyClozeTextUiState(
            text = "Vergeet niet om je paspoort mee te nemen.",
            answerRanges = listOf(28..30, 35..39),
            filled = true,
        )

        val display = state.toDisplayText()

        assertEquals("Vergeet niet om je paspoort mee te nemen.", display.text)
        assertEquals(listOf(28..30, 35..39), display.answerRanges)
    }

    @Test
    fun backSideStripsNestedTagsFromAnswerRange() {
        val state = StudyClozeTextUiState(
            text = "take <w>away</w> today.",
            answerRanges = listOf(0..15),
            filled = true,
        )

        val display = state.toDisplayText()

        assertEquals("take away today.", display.text)
        assertEquals(listOf(0..8), display.answerRanges)
    }

    @Test
    fun frontSideUsesPlainAnswerLengthForNestedTags() {
        val state = StudyClozeTextUiState(
            text = "take <w>away</w> today.",
            answerRanges = listOf(0..15),
        )

        val display = state.toDisplayText()

        assertEquals("\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0 today.", display.text)
        assertEquals(listOf(0..8), display.answerRanges)
    }

    private fun sessionCard(
        variant: CardVariant,
        example: ExamplePair? = null,
        studiedSenseIds: Set<String> = emptySet(),
        extraSenses: List<LanguageCardResponseSense> = emptyList(),
        synonyms: List<String> = emptyList(),
        primaryExampleText: String = "Het was zo <w>gezellig</w>.",
        primaryExampleTranslation: String = "It was so cosy.",
    ): SessionCard {
        return SessionCard(
            card = Card(
                id = Uuid.parse("00000000-0000-0000-0000-000000000201"),
                senseId = Uuid.parse(PrimarySenseId),
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
            wordResult = WordResult(
                card = languageCard(extraSenses, synonyms, primaryExampleText, primaryExampleTranslation),
            ),
            senseId = PrimarySenseId,
            example = example,
            studiedSenseIds = studiedSenseIds,
        )
    }

    private fun languageCard(
        extraSenses: List<LanguageCardResponseSense>,
        synonyms: List<String>,
        primaryExampleText: String,
        primaryExampleTranslation: String,
    ): LanguageCard =
        LanguageCard(
            lemma = "gezellig",
            zipfFrequency = 4.2f,
            entries = listOf(
                LanguageCardPosEntry(
                    pos = PartOfSpeech.ADJECTIVE,
                    formsViews = emptyList(),
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = PrimarySenseId,
                            senseDefinition = "een gevoel van warmte",
                            learnerLevel = LearnerLevel.A2,
                            frequency = SenseFrequency.HIGH,
                            semanticGroupId = "warmth",
                            examples = listOf(
                                LanguageCardExample(
                                    text = primaryExampleText,
                                    targetLangTranslations = mapOf(Language.ENGLISH to primaryExampleTranslation),
                                ),
                            ),
                            targetLangDefinitions = mapOf(Language.ENGLISH to "a feeling of warmth"),
                            synonyms = synonyms,
                            translations = mapOf(
                                Language.ENGLISH to listOf(
                                    LanguageCardTranslation(targetLangWord = "cosy"),
                                    LanguageCardTranslation(targetLangWord = "sociable"),
                                ),
                            ),
                        ),
                    ) + extraSenses,
                ),
            ),
        )

    private fun buildSense(
        senseId: String,
        definition: String,
        translation: String,
        exampleText: String,
        exampleTranslation: String,
    ): LanguageCardResponseSense =
        LanguageCardResponseSense(
            senseId = senseId,
            senseDefinition = definition,
            learnerLevel = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH,
            semanticGroupId = senseId,
            examples = listOf(
                LanguageCardExample(
                    text = exampleText,
                    targetLangTranslations = mapOf(Language.ENGLISH to exampleTranslation),
                ),
            ),
            targetLangDefinitions = mapOf(Language.ENGLISH to definition),
            translations = mapOf(
                Language.ENGLISH to listOf(LanguageCardTranslation(targetLangWord = translation)),
            ),
        )
}

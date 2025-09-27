package com.slovy.slovymovyapp.ui.word

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LanguageCardTrait
import com.slovy.slovymovyapp.data.remote.LanguageCardTranslation
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.NameType
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.data.remote.TraitType
import org.jetbrains.compose.ui.tooling.preview.Preview

private fun sampleContentState(): WordDetailUiState.Content = sampleCard().toContentUiState()

@Preview
@Composable
private fun WordDetailScreenPreviewContent() {
    WordDetailScreenContent(state = sampleContentState())
}

@Preview
@Composable
private fun WordDetailScreenPreviewCollapsed() {
    val base = sampleContentState()
    val collapsedEntries = base.entries.map { entryState ->
        entryState.copy(
            expanded = false,
            formsExpanded = false,
            senses = entryState.senses.map { senseState ->
                senseState.copy(
                    expanded = false,
                    examplesExpanded = false,
                    languageExpanded = senseState.languageExpanded.mapValues { false }
                )
            }
        )
    }
    WordDetailScreenContent(
        state = base.copy(entries = collapsedEntries)
    )
}

@Preview
@Composable
private fun WordDetailScreenPreviewEmpty() {
    WordDetailScreenContent(state = WordDetailUiState.Empty(lemma = "testing"))
}

@Preview
@Composable
private fun WordDetailScreenPreviewError() {
    WordDetailScreenContent(state = WordDetailUiState.Error(message = "Unable to load word"))
}

@Preview
@Composable
private fun WordDetailScreenPreviewLoading() {
    WordDetailScreenContent(state = WordDetailUiState.Loading)
}


internal fun sampleCard(): LanguageCard {
    return LanguageCard(
        lemma = "testing",
        entries = listOf(
            // Verb entry
            LanguageCardPosEntry(
                pos = PartOfSpeech.VERB,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "f8731e0a-3d06-4a0f-af1c-98de00309b76",
                        senseDefinition = "The present participle or gerund form of the verb 'to test', meaning to perform an examination or evaluation.",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "action_of_testing",
                        nameType = null,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The scientist is <w>testing</w> the new hypothesis in the lab.",
                                targetLangTranslations = mapOf("ru" to "Учёный <w>проверяет</w> новую гипотезу в лаборатории.")
                            ),
                            LanguageCardExample(
                                text = "<w>Testing</w> the water before swimming is a good idea.",
                                targetLangTranslations = mapOf("ru" to "<w>Проверять</w> воду перед плаванием — хорошая идея.")
                            ),
                            LanguageCardExample(
                                text = "They spent all morning <w>testing</w> out the new equipment.",
                                targetLangTranslations = mapOf("ru" to "Они всё утро <w>проверяли</w> новое оборудование.")
                            )
                        ),
                        synonyms = listOf("examining", "evaluating", "checking", "trying out"),
                        commonPhrases = listOf("testing out", "testing for", "testing positive/negative"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.FORM,
                                "present participle and gerund form of 'test'"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            "ru" to "Причастие настоящего времени или герундий от глагола 'to test', означающее выполнение проверки или оценки."
                        ),
                        translations = mapOf(
                            "ru" to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "тестирование",
                                    targetLangSenseClarification = "Процесс проверки или испытания чего-либо. Используется как отглагольное существительное (герундий)."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "проверка",
                                    targetLangSenseClarification = "Более общее слово для обозначения процесса контроля или испытания, часто используется как герундий."
                                )
                            )
                        )
                    )
                )
            ),
            // Noun entry
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "16f3dcde-882f-464a-8f53-0843847294b2",
                        senseDefinition = "The process of checking something to see if it works correctly or meets certain standards.",
                        learnerLevel = LearnerLevel.B2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "evaluation_process",
                        nameType = null,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The software is currently undergoing rigorous <w>testing</w> before its release.",
                                targetLangTranslations = mapOf("ru" to "В настоящее время программное обеспечение проходит тщательное <w>тестирование</w> перед выпуском.")
                            ),
                            LanguageCardExample(
                                text = "Quality <w>testing</w> is essential to ensure product reliability.",
                                targetLangTranslations = mapOf("ru" to "<w>Тестирование</w> качества необходимо для обеспечения надёжности продукта.")
                            ),
                            LanguageCardExample(
                                text = "The new car went through extensive road <w>testing</w> to ensure its safety.",
                                targetLangTranslations = mapOf("ru" to "Новый автомобиль прошёл длительные дорожные <w>испытания</w> для обеспечения его безопасности.")
                            )
                        ),
                        synonyms = listOf("examination", "evaluation", "trial", "assessment"),
                        commonPhrases = listOf(
                            "product testing",
                            "software testing",
                            "road testing",
                            "quality testing"
                        ),
                        targetLangDefinitions = mapOf(
                            "ru" to "Процесс проверки чего-либо с целью убедиться, что оно работает правильно или соответствует определённым стандартам."
                        ),
                        translations = mapOf(
                            "ru" to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "тестирование",
                                    targetLangSenseClarification = "Процесс испытания или проверки чего-либо, особенно в техническом или научном контексте (например, программного обеспечения, оборудования)."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "испытание",
                                    targetLangSenseClarification = "Проверка свойств, качеств кого-либо или чего-либо, часто в сложных или экстремальных условиях (например, дорожные испытания)."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "проверка",
                                    targetLangSenseClarification = "Более общее слово, означающее контроль или обследование с целью выяснения правильности, состояния чего-либо."
                                )
                            )
                        )
                    )
                )
            ),
            // Adjective entry
            LanguageCardPosEntry(
                pos = PartOfSpeech.ADJECTIVE,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "5e1a3249-2d90-42a3-ab1d-c18a2c92f4d0",
                        senseDefinition = "Causing difficulty or requiring a lot of effort and ability.",
                        learnerLevel = LearnerLevel.B1,
                        frequency = SenseFrequency.MIDDLE,
                        semanticGroupId = "difficulty",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The exam was very <w>testing</w>, but I think I did well.",
                                targetLangTranslations = mapOf("ru" to "Экзамен был очень <w>сложным</w>, но, думаю, я справился хорошо.")
                            ),
                            LanguageCardExample(
                                text = "She faced a <w>testing</w> challenge when she started her new job.",
                                targetLangTranslations = mapOf("ru" to "Она столкнулась с <w>трудной</w> задачей, когда начала работать на новой работе.")
                            ),
                            LanguageCardExample(
                                text = "It was a <w>testing</w> time for everyone involved in the project.",
                                targetLangTranslations = mapOf("ru" to "Это было <w>напряжённое</w> время для всех, кто участвовал в проекте.")
                            )
                        ),
                        synonyms = listOf("challenging", "difficult", "demanding"),
                        antonyms = listOf("easy", "simple"),
                        commonPhrases = listOf("a testing time", "a testing period", "a testing challenge"),
                        targetLangDefinitions = mapOf(
                            "ru" to "Трудный, требующий больших усилий, напряжения или способностей."
                        ),
                        translations = mapOf(
                            "ru" to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "сложный",
                                    targetLangSenseClarification = "Наиболее общий и частый перевод, означает 'трудный для выполнения, понимания или решения'."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "трудный",
                                    targetLangSenseClarification = "Синоним 'сложный', подчёркивает необходимость приложить усилия и преодолеть препятствия."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "напряжённый",
                                    targetLangSenseClarification = "Описывает период времени или ситуацию, требующую больших физических или умственных усилий и вызывающую стресс (например, 'a testing time')."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}
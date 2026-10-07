package com.slovy.slovymovyapp.ui.developer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.ui.study.StudyCardBackUiState
import com.slovy.slovymovyapp.ui.study.StudyCardSide
import com.slovy.slovymovyapp.ui.study.StudyCardUiState
import com.slovy.slovymovyapp.ui.study.StudyExampleUiState
import com.slovy.slovymovyapp.ui.study.StudyRating
import com.slovy.slovymovyapp.ui.study.StudyRatingUiState
import com.slovy.slovymovyapp.ui.study.StudySessionProgressUiState
import com.slovy.slovymovyapp.ui.study.StudySessionScreenContent
import com.slovy.slovymovyapp.ui.study.StudySessionUiState
import com.slovy.slovymovyapp.ui.study.StudySynonymUiState
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.study_chip_type

/**
 * Developer-only: the typed spelling card on its own, cycling through a few built-in Dutch words
 * so its mechanics can be tried on a device without learning data, scheduling or a network. A
 * rating simply moves to the next word; nothing is stored.
 */
class TypedCardPlaygroundViewModel : ViewModel() {

    private val cards: List<StudyCardUiState.Typed> = TypedCardPlaygroundSamples.cards
    private var index: Int = 0

    var state by mutableStateOf(activeState(cards.first()))
        private set

    fun updateInput(value: TextFieldValue) {
        val active = state
        if (active.side != StudyCardSide.FRONT) return
        state = active.copy(card = (active.card as StudyCardUiState.Typed).withInput(value))
    }

    fun revealHint() {
        val active = state
        if (active.side != StudyCardSide.FRONT) return
        val hinted = (active.card as StudyCardUiState.Typed).withNextLetterHint() ?: return
        state = active.copy(card = hinted)
    }

    fun check() {
        val active = state
        if (active.side != StudyCardSide.FRONT) return
        val checked = (active.card as StudyCardUiState.Typed).checked() ?: return
        state = active.copy(card = checked, side = StudyCardSide.BACK)
    }

    fun showAnswer() {
        val active = state
        if (active.side != StudyCardSide.FRONT) return
        state = active.copy(card = (active.card as StudyCardUiState.Typed).answerShown(), side = StudyCardSide.BACK)
    }

    fun rate() {
        if (state.side != StudyCardSide.BACK) return
        index = (index + 1) % cards.size
        state = activeState(cards[index])
    }

    fun setViewedSense(senseId: String) {
        state = state.copy(viewedSenseId = senseId)
    }

    private fun activeState(card: StudyCardUiState.Typed) = StudySessionUiState.Active(
        progress = StudySessionProgressUiState(current = index + 1, total = cards.size),
        card = card,
        side = StudyCardSide.FRONT,
        ratingOptions = StudyRating.entries.map { StudyRatingUiState(rating = it, intervalLabel = "") },
        viewedSenseId = card.activeSenseId,
    )
}

@Composable
fun TypedCardPlaygroundScreen(
    viewModel: TypedCardPlaygroundViewModel,
    onClose: () -> Unit,
) {
    StudySessionScreenContent(
        state = viewModel.state,
        onCancel = onClose,
        onEnd = onClose,
        onTypedInputChange = viewModel::updateInput,
        onRevealTypedHint = viewModel::revealHint,
        onCheckTypedAnswer = viewModel::check,
        onShowTypedAnswer = viewModel::showAnswer,
        onRate = { viewModel.rate() },
        onViewedSenseChange = viewModel::setViewedSense,
    )
}

/** Sample words for the playground. Dutch with English cues, including a separable verb and an accent. */
internal object TypedCardPlaygroundSamples {

    val cards: List<StudyCardUiState.Typed> = listOf(
        typed(
            id = "gezellig",
            prompt = "cosy, sociable",
            pos = PartOfSpeech.ADJECTIVE,
            lemma = "gezellig",
            definition = "Een sfeer van warmte, gemak en samenzijn.",
            definitionTranslation = "A warm, relaxed, convivial atmosphere.",
            example = "Bij Marja is het altijd <w>gezellig</w>." to "It's always cosy at Marja's.",
            synonyms = listOf("knus", "prettig", "aangenaam"),
        ),
        typed(
            id = "fiets",
            prompt = "bicycle",
            pos = PartOfSpeech.NOUN,
            lemma = "fiets",
            definition = "Een voertuig met twee wielen dat je met je voeten aandrijft.",
            definitionTranslation = "A two-wheeled vehicle you drive with your feet.",
            example = "Mijn <w>fiets</w> staat op slot." to "My bicycle is locked.",
            synonyms = listOf("rijwiel"),
        ),
        typed(
            id = "aanschuiven",
            prompt = "to join (at the table)",
            pos = PartOfSpeech.VERB,
            lemma = "aanschuiven",
            definition = "Bij een groep aan tafel gaan zitten.",
            definitionTranslation = "To sit down with a group at the table.",
            example = "Schuif gerust <w>aan</w>, er is genoeg." to "Feel free to join us, there's plenty.",
            synonyms = listOf("aansluiten"),
        ),
        typed(
            id = "verantwoordelijkheid",
            prompt = "responsibility",
            pos = PartOfSpeech.NOUN,
            lemma = "verantwoordelijkheid",
            definition = "De plicht om voor iets of iemand te zorgen en daarop aangesproken te kunnen worden.",
            definitionTranslation = "The duty to take care of something or someone and to answer for it.",
            example = "Zij draagt de <w>verantwoordelijkheid</w> voor het project." to "She carries the responsibility for the project.",
            synonyms = listOf("aansprakelijkheid"),
        ),
        typed(
            id = "ontbijt",
            prompt = "De eerste maaltijd van de dag, meestal in de ochtend.",
            isDefinitionPrompt = true,
            pos = PartOfSpeech.NOUN,
            lemma = "ontbijt",
            definition = "De eerste maaltijd van de dag, meestal in de ochtend.",
            definitionTranslation = "The first meal of the day, usually in the morning.",
            example = "Het <w>ontbijt</w> staat klaar." to "Breakfast is ready.",
            synonyms = emptyList(),
        ),
        typed(
            id = "cafe",
            prompt = "pub, bar",
            pos = PartOfSpeech.NOUN,
            lemma = "café",
            definition = "Een plek waar je iets kunt drinken en mensen kunt ontmoeten.",
            definitionTranslation = "A place to have a drink and meet people.",
            example = "We gaan vanavond naar het <w>café</w>." to "We're going to the pub tonight.",
            synonyms = listOf("kroeg", "bar"),
        ),
    )

    private fun typed(
        id: String,
        prompt: String,
        isDefinitionPrompt: Boolean = false,
        pos: PartOfSpeech,
        lemma: String,
        definition: String,
        definitionTranslation: String,
        example: Pair<String, String>,
        synonyms: List<String>,
    ) = StudyCardUiState.Typed(
        id = id,
        chipLabel = UiText.Resource(Res.string.study_chip_type),
        promptText = prompt,
        isDefinitionPrompt = isDefinitionPrompt,
        partOfSpeech = UiText.Resource(pos.displayName),
        lemma = lemma,
        languageCode = "nl",
        back = StudyCardBackUiState(
            headline = lemma,
            isLemmaHeadline = true,
            translations = if (isDefinitionPrompt) null else prompt,
            definition = definition,
            definitionTranslation = definitionTranslation,
            examples = listOf(StudyExampleUiState(text = example.first, translation = example.second)),
            synonyms = synonyms.map { StudySynonymUiState(it) },
            audioText = lemma,
        ),
    )
}

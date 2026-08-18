package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.speech.LemmaAudioControl
import com.slovy.slovymovyapp.speech.RowAudioPhase
import com.slovy.slovymovyapp.ui.SpeakerVector
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.components.appendWithCenteredBullets
import com.slovy.slovymovyapp.ui.components.appendWithMutedCenteredBullets
import com.slovy.slovymovyapp.ui.components.centeredBulletInlineContent
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.text.Typography.bullet

data class SenseCardData(
    val senseId: String,
    val lemma: String,
    val showLemma: Boolean = true,
    val sense: LanguageCardResponseSense? = null,
    val pos: PartOfSpeech? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val translationLoading: Boolean = false,
    val translationError: UiText? = null,
    val diagnosticInfoOnError: String? = null,
    val ambiguousTranslations: Set<String> = emptySet(),
    // Summary shown while the full sense is not loaded (e.g. list rows before expansion).
    val collapsedDefinition: String? = null,
    val collapsedLearnerLevel: LearnerLevel? = null,
    val collapsedFrequency: SenseFrequency? = null,
)

@Composable
internal fun SenseCard(
    data: SenseCardData,
    state: SenseUiState,
    onToggle: () -> Unit,
    onPositioned: (String, Float) -> Unit = { _, _ -> },
    onFavoriteToggle: () -> Unit = {},
    relatedWords: Map<String, RelatedWord> = emptyMap(),
    onWordClick: (String) -> Unit = {},
    onViewFullDetails: (() -> Unit)? = null,
    favoriteLemmas: Set<String> = emptySet(),
    // Inline lemma speaker (My words / list detail). When null no speaker is shown — Word details
    // renders its own hero speaker, so it leaves this off.
    lemmaAudio: LemmaAudioControl? = null,
) {
    val sense = data.sense
    val translationBasedHeader = remember(data.senseId, sense?.translations) { sense?.translationsHeader() }

    val expanded = state.expanded
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onPositioned(data.senseId, coordinates.positionInWindow().y)
            },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(
            containerColor = colorForLemma(data.lemma, MaterialTheme.colorScheme.surface, LocalIsDarkTheme.current)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    when {
                        data.error != null -> {
                            ErrorPlaceholder(data.error)
                            data.diagnosticInfoOnError?.let { info ->
                                Text(
                                    text = info,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        data.loading -> LoadingPlaceholder(stringResource(Res.string.word_details_loading_meaning))
                        data.translationError != null -> ErrorPlaceholder(data.translationError)
                        data.translationLoading -> LoadingPlaceholder(stringResource(Res.string.word_details_preparing_translation))
                    }
                    if (data.showLemma) {
                        if (lemmaAudio != null) {
                            LemmaWithSpeaker(
                                lemma = data.lemma,
                                control = lemmaAudio,
                            )
                        } else {
                            HighlightedText(
                                text = data.lemma,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = MaterialTheme.serifFontFamily
                                )
                            )
                        }
                    }
                    if (sense != null) {
                        if (translationBasedHeader == null) {
                            HighlightedText(
                                text = sense.senseDefinition,
                                style = MaterialTheme.typography.titleMedium,
                                clickableWords = relatedWords.keys,
                                onWordClick = onWordClick
                            )
                        } else {
                            TranslationHeader(
                                sense = sense,
                                ambiguous = data.ambiguousTranslations,
                                clickableWords = relatedWords.keys,
                                onWordClick = onWordClick
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            LevelAndFrequencyRow(
                                level = sense.learnerLevel,
                                frequency = sense.frequency,
                                nameType = sense.nameType,
                            )
                        }
                    } else if (data.error == null && data.collapsedDefinition != null) {
                        HighlightedText(
                            text = data.collapsedDefinition,
                            style = MaterialTheme.typography.titleMedium,
                            clickableWords = relatedWords.keys,
                            onWordClick = onWordClick
                        )
                        if (data.collapsedLearnerLevel != null && data.collapsedFrequency != null) {
                            LevelAndFrequencyRow(
                                level = data.collapsedLearnerLevel,
                                frequency = data.collapsedFrequency,
                                nameType = null,
                            )
                        }
                    } else if (data.error == null && !data.loading) {
                        HighlightedText(
                            text = stringResource(Res.string.word_details_meaning_load_when_expanded),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.showFavoriteToggle) {
                        IconButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (state.favorite) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                tint = if (state.favorite) {
                                    FavoriteAccentColor
                                } else {
                                    LocalContentColor.current
                                },
                                contentDescription = stringResource(Res.string.search_content_desc_favorite)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                        contentDescription = if (expanded) {
                            stringResource(Res.string.word_details_collapse_sense)
                        } else {
                            stringResource(Res.string.word_details_expand_sense)
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && sense != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                sense?.let {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = AppSpacing.lg,
                                    end = AppSpacing.lg,
                                    top = AppSpacing.sm,
                                    bottom = AppSpacing.lg,
                                ),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                        ) {
                            if (sense.targetLangDefinitions.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                    SectionLabel(stringResource(Res.string.word_details_definition))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        HighlightedText(
                                            text = sense.targetLangDefinitions.map { definition ->
                                                definition.value.replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() }
                                            }.joinToString("\n"),
                                            clickableWords = relatedWords.keys,
                                            onWordClick = onWordClick,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = MaterialTheme.serifFontFamily
                                            ),
                                        )
                                        if (translationBasedHeader != null && sense.senseDefinition.isNotBlank()) {
                                            HighlightedText(
                                                text = sense.senseDefinition,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                clickableWords = relatedWords.keys,
                                                onWordClick = onWordClick,
                                            )
                                        }
                                    }
                                }
                            }

                            if (sense.traits.isNotEmpty()) {
                                TraitsList(traits = sense.traits)
                            }

                            if (sense.examples.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                    SectionLabel(text = stringResource(Res.string.word_details_examples))
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        sense.examples.forEach { ex ->
                                            ExampleItem(
                                                example = ex,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            EntryList(
                                stringResource(Res.string.word_details_common_phrases),
                                sense.commonPhrases,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                relatedWords = relatedWords,
                                onWordClick = onWordClick,
                                favoriteLemmas = favoriteLemmas
                            )
                            val (synonymBg, synonymText) = colorsForSynonyms()
                            EntryList(
                                stringResource(Res.string.word_details_synonyms),
                                sense.synonyms,
                                synonymBg,
                                synonymText,
                                relatedWords = relatedWords,
                                onWordClick = onWordClick,
                                favoriteLemmas = favoriteLemmas
                            )
                            val (antonymBg, antonymText) = colorsForAntonyms()
                            EntryList(
                                stringResource(Res.string.word_details_antonyms),
                                sense.antonyms,
                                antonymBg,
                                antonymText,
                                relatedWords = relatedWords,
                                onWordClick = onWordClick,
                                favoriteLemmas = favoriteLemmas
                            )
                        }
                    }
                }
            }

            // Footer action for viewing full word details (used in Favorites)
            AnimatedVisibility(
                visible = expanded && onViewFullDetails != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = AppSpacing.lg),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewFullDetails?.invoke() }
                            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.word_details_see_full_word),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lemma with an inline speaker glyph that follows the last fragment on wrap (spec §3). The glyph is
 * an inline placeholder so it stays bound to the word's text run and never detaches or truncates;
 * the 44dp tap target is grown past the placeholder with [Modifier.requiredSize] so it never adds
 * row height. Colour/glyph mirror the Word-details speaker.
 */
@Composable
private fun LemmaWithSpeaker(
    lemma: String,
    control: LemmaAudioControl,
) {
    val playing = control.phase == RowAudioPhase.PLAYING
    val playLabel = stringResource(Res.string.word_details_action_play_word)
    val stopLabel = stringResource(Res.string.word_details_action_stop)
    // Secondary ink, dialled back so the word stays the hero (design review #3). Theme-aware in
    // both light/dark via onSurfaceVariant.
    val speakerTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val text = buildAnnotatedString {
        append(lemma)
        // A normal space lets the glyph wrap onto the next line with the word rather than overflow.
        append(' ')
        appendInlineContent(SPEAKER_INLINE_ID, "🔊")
    }
    val inlineContent = mapOf(
        SPEAKER_INLINE_ID to InlineTextContent(
            // Width kept close to the glyph so the word-to-glyph gap reads tight (design review #4);
            // height tracks ~1em of the lemma. The 44dp hit box overflows this slot, so the
            // placeholder only governs layout/spacing, never the tap target.
            Placeholder(
                width = 1.2.em,
                height = 1.1.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            )
        ) {
            // Outer box fills the placeholder slot; the inner 44dp target overflows symmetrically
            // (centred) so the glyph stays aligned to the lemma while the hit box extends out.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .requiredSize(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClick = control.onToggle,
                            role = Role.Button,
                            onClickLabel = if (playing) stopLabel else playLabel,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        control.phase == RowAudioPhase.PREPARING -> SpinningProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = speakerTint,
                        )

                        else -> Icon(
                            imageVector = if (playing) Icons.Filled.StopCircle else SpeakerVector,
                            contentDescription = if (playing) stopLabel else playLabel,
                            tint = speakerTint,
                            // Nudge the glyph to the lemma's optical midline (x-height centre), which
                            // reads slightly below the line centre (design review #2). Visual only —
                            // the hit box is unmoved.
                            modifier = Modifier.size(18.dp).offset(y = 1.dp),
                        )
                    }
                }
            }
        }
    )
    Text(
        text = text,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = MaterialTheme.serifFontFamily
        ),
    )
}

private const val SPEAKER_INLINE_ID = "lemma_speaker"

@Composable
internal fun TraitsList(traits: List<LanguageCardTrait>) {
    if (traits.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        SectionLabel(stringResource(Res.string.word_details_usage_context))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 11.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            traits.forEach { trait ->
                val traitName = stringResource(trait.traitType.displayName)
                val annotatedText = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        append(traitName)
                    }
                    if (trait.comment.isNotBlank()) {
                        append(": ")
                        withStyle(
                            style = SpanStyle(
                                fontStyle = FontStyle.Italic
                            )
                        ) {
                            append(trait.comment)
                        }
                    }
                }
                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
internal fun ExampleItem(
    example: LanguageCardExample,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            ExampleText(
                text = example.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = FontStyle.Normal,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.1f
                ),
            )
            if (example.targetLangTranslations.isNotEmpty()) {
                example.targetLangTranslations.forEach { (_, translation) ->
                    ExampleText(
                        text = translation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.1f
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExampleText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val highlight = SpanStyle(
        fontWeight = FontWeight.Bold,
        color = style.color
    )
    val annotated = buildAnnotatedString {
        appendTextWithW(this, text, highlight, highlight, emptySet())
    }
    val bulletColor = style.color.takeOrElse { LocalContentColor.current }

    Text(
        text = annotated,
        style = style,
        modifier = modifier,
        inlineContent = remember(bulletColor) { centeredBulletInlineContent(bulletColor) },
    )
}

@Composable
internal fun LevelAndFrequencyRow(
    level: LearnerLevel,
    frequency: SenseFrequency,
    nameType: NameType?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (lc, lcc) = colorsForLevel(level)
        val (fc, fcc) = colorsForFrequency(frequency)
        MetaBadge(text = level.name, containerColor = lc, contentColor = lcc)
        MetaBadge(text = stringResource(frequency.label), containerColor = fc, contentColor = fcc)
        if (nameType != null && nameType != NameType.NO) {
            val (nc, ncc) = colorsForNameType(nameType)
            MetaBadge(text = stringResource(nameType.displayName), containerColor = nc, contentColor = ncc)
        }
    }
}

internal fun LanguageCardResponseSense.collectLanguages(): List<Language> {
    val ordered = linkedSetOf<Language>()
    ordered += targetLangDefinitions.keys
    ordered += translations.keys
    examples.forEach { ex -> ordered += ex.targetLangTranslations.keys }
    return ordered.toList()
}

@Composable
internal fun TranslationHeader(
    sense: LanguageCardResponseSense,
    ambiguous: Set<String>,
    clickableWords: Set<String> = emptySet(),
    onWordClick: (String) -> Unit = {}
) {
    val hasClarifications = ambiguous.isNotEmpty() &&
            sense.translations.values.flatten().any { it.targetLangWord in ambiguous && it.targetLangSenseClarification != null }

    if (!hasClarifications) {
        HighlightedText(
            text = sense.translationsHeader() ?: "",
            style = MaterialTheme.typography.titleMedium,
            clickableWords = clickableWords,
            onWordClick = onWordClick
        )
        return
    }

    val entries = sense.cleanedTranslationEntries(sense.translations.keys)
    val multiLang = entries.size > 1
    val clarificationColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.titleMedium
    val bulletColor = LocalContentColor.current
    val bulletInlineContent = remember(bulletColor, clarificationColor) {
        centeredBulletInlineContent(color = bulletColor, mutedColor = clarificationColor)
    }
    entries.forEach { (language, langTranslations) ->
        Text(
            text = buildClarificationRow(
                langTranslations,
                language,
                ambiguous,
                multiLang,
                clarificationColor,
            ),
            style = style,
            inlineContent = bulletInlineContent,
        )
    }
}

internal fun buildClarificationRow(
    langTranslations: List<LanguageCardTranslation>,
    language: Language,
    ambiguous: Set<String>,
    multiLang: Boolean,
    clarificationColor: Color = Color.Unspecified
) = buildAnnotatedString {
    if (multiLang) appendWithCenteredBullets(this, "$bullet ")
    langTranslations.orderedByIdx().forEachIndexed { index, translation ->
        if (index > 0) append(language.wordSeparator)
        val word = translation.targetLangWord
        append(word)
        val clarification = translation.targetLangSenseClarification
        if (word in ambiguous && clarification != null) {
            withStyle(SpanStyle(color = clarificationColor)) {
                // The span only colors text; the drawn dot must be routed to the muted inline
                // content so it matches the clarification color.
                appendWithMutedCenteredBullets(this, " $bullet $clarification")
            }
        }
    }
}

internal fun LanguageCardResponseSense.translationsHeader(): String? {
    val lines = translationLines(translations.keys)
    return joinLanguageLines(lines, bulleted = lines.size > 1)
}

// One cleaned line per requested language that actually has content: words are blank-filtered
// and de-duplicated, languages ordered stably by enum order.
internal fun LanguageCardResponseSense.translationLines(languages: Set<Language>): List<String> =
    cleanedTranslationEntries(languages).map { (language, entries) ->
        entries.map { it.targetLangWord }.distinct().joinToString(separator = language.wordSeparator)
    }

// Per-language translation entries with renderable content: blank words dropped, exact duplicate
// entries collapsed (the same word with a different clarification survives — it carries distinct
// information), words ordered by idx, languages ordered stably by enum order. Languages left with
// no content are omitted entirely so they produce no empty rows and don't count as an extra
// language for bullet formatting.
internal fun LanguageCardResponseSense.cleanedTranslationEntries(
    languages: Set<Language>,
): List<Pair<Language, List<LanguageCardTranslation>>> =
    translations.entries
        .filter { it.key in languages }
        .sortedBy { it.key }
        .mapNotNull { (language, entries) ->
            entries.orderedByIdx()
                .filter { it.targetLangWord.isNotBlank() }
                .distinctBy { it.targetLangWord to it.targetLangSenseClarification }
                .takeIf { it.isNotEmpty() }
                ?.let { language to it }
        }

// The single renderer of the bullet-per-language block layout shared by the word-details header
// and the study card backs. Callers decide `bulleted` so all blocks on one surface can agree.
internal fun joinLanguageLines(lines: List<String>, bulleted: Boolean): String? {
    if (lines.isEmpty()) {
        return null
    }
    return lines.joinToString(separator = "\n") { line -> if (bulleted) "$bullet $line" else line }
}

private fun List<LanguageCardTranslation>.orderedByIdx(): List<LanguageCardTranslation> =
    sortedBy { it.idx }

internal fun colorForLemma(lemma: String?, baseColor: Color, isDark: Boolean = false): Color {
    if (lemma == null) return baseColor

    val hash = lemma.hashCode()

    val hue = if (isDark) {
        200f + (hash and 0x3FF) / 1023f * 120f  // 200–320°: blue → purple
    } else {
        (hash and 0x3FF) / 1023f * 360f
    }
    val saturation = 0.45f + ((hash shr 10) and 0x3F) / 63f * 0.30f
    val lightness = if (isDark) {
        0.55f + ((hash shr 16) and 0x1F) / 31f * 0.20f
    } else {
        0.45f + ((hash shr 16) and 0x1F) / 31f * 0.20f
    }
    val tintColor = Color.hsl(hue = hue, saturation = saturation, lightness = lightness)

    val percent = 0.08f
    return baseColor.copy(
        red = (baseColor.red * (1.0f - percent) + tintColor.red * percent),
        green = (baseColor.green * (1.0f - percent) + tintColor.green * percent),
        blue = (baseColor.blue * (1.0f - percent) + tintColor.blue * percent)
    )
}

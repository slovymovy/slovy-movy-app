package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import kotlin.text.Typography.bullet

data class SenseCardData(
    val senseId: String,
    val lemma: String,
    val showLemma: Boolean = true,
    val sense: LanguageCardResponseSense? = null,
    val pos: PartOfSpeech? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val translationLoading: Boolean = false,
    val translationError: String? = null,
    val diagnosticInfoOnError: String? = null,
    val ambiguousTranslations: Set<String> = emptySet()
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
    favoriteLemmas: Set<String> = emptySet()
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
            containerColor = colorForLemma(data.lemma, MaterialTheme.colorScheme.surface)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

                        data.loading -> LoadingPlaceholder("Loading meaning…")
                        data.translationError != null -> ErrorPlaceholder(data.translationError)
                        data.translationLoading -> LoadingPlaceholder("Preparing translation…")
                    }
                    if (data.showLemma) {
                        HighlightedText(
                            text = data.lemma,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (sense != null) {
                        if (translationBasedHeader == null) {
                            HighlightedText(
                                text = sense.senseDefinition,
                                style = MaterialTheme.typography.titleMedium,
                                clickableWords = relatedWords.keys,
                                onWordClick = onWordClick
                            )
                        } else if (sense.hasAmbiguousClarifications(data.ambiguousTranslations)) {
                            TranslationsHeaderWithClarifications(
                                sense = sense,
                                ambiguous = data.ambiguousTranslations,
                            )
                        } else {
                            HighlightedText(
                                text = translationBasedHeader,
                                style = MaterialTheme.typography.titleMedium,
                                clickableWords = relatedWords.keys,
                                onWordClick = onWordClick
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LevelAndFrequencyRow(
                                level = sense.learnerLevel,
                                frequency = sense.frequency,
                                nameType = sense.nameType,
                            )
                        }
                    } else if (data.error == null && !data.loading) {
                        HighlightedText(
                            text = "Meaning will load when expanded",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    Color(0xFFC46060) // Warm dusty rose
                                } else {
                                    LocalContentColor.current
                                },
                                contentDescription = "Favorites"
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                        contentDescription = if (expanded) {
                            "Collapse sense"
                        } else {
                            "Expand sense"
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (sense.targetLangDefinitions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel("Definition")
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (translationBasedHeader != null) {
                                        HighlightedText(
                                            text = sense.senseDefinition,
                                            clickableWords = relatedWords.keys,
                                            onWordClick = onWordClick,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    HighlightedText(
                                        text = sense.targetLangDefinitions.map { definition ->
                                            definition.value.replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() }
                                        }.joinToString("\n"),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                    )
                                }
                            }
                        }

                        if (sense.traits.isNotEmpty()) {
                            TraitsList(traits = sense.traits)
                        }

                        if (sense.examples.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel(text = "Examples")
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
                            "Common phrases",
                            sense.commonPhrases,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            relatedWords = relatedWords,
                            onWordClick = onWordClick,
                            favoriteLemmas = favoriteLemmas
                        )
                        val (synonymBg, synonymText) = colorsForSynonyms()
                        EntryList(
                            "Synonyms",
                            sense.synonyms,
                            synonymBg,
                            synonymText,
                            relatedWords = relatedWords,
                            onWordClick = onWordClick,
                            favoriteLemmas = favoriteLemmas
                        )
                        val (antonymBg, antonymText) = colorsForAntonyms()
                        EntryList(
                            "Antonyms",
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

            // Footer action for viewing full word details (used in Favorites)
            AnimatedVisibility(
                visible = expanded && onViewFullDetails != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewFullDetails?.invoke() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "See full word",
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

@Composable
internal fun TraitsList(traits: List<LanguageCardTrait>) {
    if (traits.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel("Usage Context")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 11.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            traits.forEach { trait ->
                val annotatedText = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        append(trait.traitType.displayName)
                    }
                    if (trait.comment.isNotBlank()) {
                        append(": ")
                        withStyle(
                            style = SpanStyle(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        appendTextWithW(this, text, highlight, highlight, emptySet()) { }
    }

    Text(
        text = annotated,
        style = style,
        modifier = modifier
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (lc, lcc) = colorsForLevel(level)
        val (fc, fcc) = colorsForFrequency(frequency)
        Badge(text = level.name, containerColor = lc, contentColor = lcc)
        Badge(text = frequency.label, containerColor = fc, contentColor = fcc)
        if (nameType != null && nameType != NameType.NO) {
            val (nc, ncc) = colorsForNameType(nameType)
            Badge(text = nameType.displayName, containerColor = nc, contentColor = ncc)
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
private fun TranslationsHeaderWithClarifications(
    sense: LanguageCardResponseSense,
    ambiguous: Set<String>,
) {
    val multiLang = sense.translations.keys.size > 1
    val clarificationColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.titleMedium
    sense.translations.entries.sortedBy { it.key }.forEach { (_, langTranslations) ->
        val annotated = buildAnnotatedString {
            if (multiLang) append("$bullet ")
            langTranslations.sortedBy { it.targetLangWord }.forEachIndexed { index, translation ->
                if (index > 0) append(", ")
                append(translation.targetLangWord)
                val clarification = translation.targetLangSenseClarification
                if (translation.targetLangWord in ambiguous && clarification != null) {
                    withStyle(SpanStyle(color = clarificationColor)) {
                        append(" $bullet $clarification")
                    }
                }
            }
        }
        Text(text = annotated, style = style)
    }
}

private fun LanguageCardResponseSense.translationsHeader(): String? {
    if (translations.isEmpty()) {
        return null
    }
    val prefix = if (translations.keys.size > 1) "$bullet " else ""
    return translations.entries.sortedBy { it.key }.joinToString(separator = "\n") {
        prefix + it.value.map { translation -> translation.targetLangWord }.sortedBy { text -> text }
            .joinToString(separator = ", ")
    }
}

private fun LanguageCardResponseSense.hasAmbiguousClarifications(ambiguous: Set<String>): Boolean {
    return translations.values.flatten().any { it.targetLangWord in ambiguous && it.targetLangSenseClarification != null }
}

internal fun colorForLemma(lemma: String?, baseColor: Color): Color {
    if (lemma == null) return baseColor

    val hash = lemma.hashCode()

    val hue = (hash and 0x3FF) / 1023f * 360f
    val saturation = 0.45f + ((hash shr 10) and 0x3F) / 63f * 0.30f
    val lightness = 0.45f + ((hash shr 16) and 0x1F) / 31f * 0.20f
    val tintColor = Color.hsl(hue = hue, saturation = saturation, lightness = lightness)

    val percent = 0.08f
    return baseColor.copy(
        red = (baseColor.red * (1.0f - percent) + tintColor.red * percent),
        green = (baseColor.green * (1.0f - percent) + tintColor.green * percent),
        blue = (baseColor.blue * (1.0f - percent) + tintColor.blue * percent)
    )
}

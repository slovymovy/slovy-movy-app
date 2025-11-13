package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.*
import kotlin.text.Typography.bullet

@Composable
internal fun SenseCard(
    lemma: String?,
    sense: LanguageCardResponseSense,
    state: SenseUiState,
    onToggle: () -> Unit,
    onPositioned: (String, Float) -> Unit = { _, _ -> },
    allSenses: List<LanguageCardResponseSense>,
    onFavoriteToggle: () -> Unit = {},
    relatedWords: Set<String> = emptySet(),
    onWordClick: (String) -> Unit = {}
) {
    val translationBasedHeader = remember(sense.senseId) { sense.translationsHeader() }

    val expanded = state.expanded
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onPositioned(sense.senseId, coordinates.positionInWindow().y)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lemma?.let {
                        HighlightedText(
                            text = it,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (translationBasedHeader == null) {
                        HighlightedText(
                            text = sense.senseDefinition,
                            style = MaterialTheme.typography.titleMedium,
                            clickableWords = relatedWords,
                            onWordClick = onWordClick
                        )
                    } else {
                        HighlightedText(
                            text = translationBasedHeader,
                            style = MaterialTheme.typography.titleMedium,
                            clickableWords = relatedWords,
                            onWordClick = onWordClick
                        )
                    }

                    LevelAndFrequencyRow(
                        level = sense.learnerLevel,
                        frequency = sense.frequency,
                        nameType = sense.nameType,
                    )
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
                                    Color.Red
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
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (sense.targetLangDefinitions.isNotEmpty()) {
                        SectionLabel("Definition")
                        if (translationBasedHeader != null) {
                            HighlightedText(
                                text = sense.senseDefinition,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        HighlightedText(
                            text = sense.targetLangDefinitions.map { definition -> definition.value.replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() } }
                                .joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    if (sense.traits.isNotEmpty()) {
                        TraitsList(traits = sense.traits)
                    }

                    if (sense.examples.isNotEmpty()) {
                        SectionLabel(text = "Examples")
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sense.examples.forEach { ex ->
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    HighlightedText(
                                        text = ex.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        clickableWords = relatedWords,
                                        onWordClick = onWordClick,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    if (ex.targetLangTranslations.isNotEmpty()) {
                                        ex.targetLangTranslations.forEach { (_, translation) ->
                                            HighlightedText(
                                                text = translation,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                clickableWords = relatedWords,
                                                onWordClick = onWordClick
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    EntryList(
                        "Common phrases",
                        sense.commonPhrases,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        clickableWords = relatedWords,
                        onWordClick = onWordClick
                    )
                    EntryList(
                        "Synonyms",
                        sense.synonyms,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        clickableWords = relatedWords,
                        onWordClick = onWordClick
                    )
                    EntryList(
                        "Antonyms",
                        sense.antonyms,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        clickableWords = relatedWords,
                        onWordClick = onWordClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TraitsList(traits: List<LanguageCardTrait>) {
    if (traits.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel("Usage Context")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            traits.forEach { trait ->
                val (tc, tcc) = colorsForTraitType(trait.traitType)

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = tc.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = trait.traitType.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = tcc
                            )
                        )
                        if (trait.comment.isNotBlank()) {
                            Text(
                                text = trait.comment,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
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

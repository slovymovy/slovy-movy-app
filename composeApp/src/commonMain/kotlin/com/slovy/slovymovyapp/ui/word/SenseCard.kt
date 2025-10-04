package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.codeToLanguage
import kotlin.text.Typography.bullet

@Composable
internal fun SenseCard(
    sense: LanguageCardResponseSense,
    state: SenseUiState,
    onToggle: () -> Unit,
    onExamplesToggle: () -> Unit,
    onLanguageToggle: (String) -> Unit,
    onPositioned: (String, Float) -> Unit = { _, _ -> },
    allSenses: List<LanguageCardResponseSense>,
    onFavoriteToggle: () -> Unit = {},
    onNavigateToDetail: () -> Unit = {}
) {
    val translationBasedHeader = remember { sense.translationsHeader() }
    val translationHeaderSuffix = remember { translationsHeaderSuffix(sense, allSenses) }

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
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (translationBasedHeader == null) {
                        HighlightedText(
                            text = sense.senseDefinition,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        HighlightedText(
                            text = translationBasedHeader,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (translationHeaderSuffix != null) {
                            HighlightedText(
                                text = translationHeaderSuffix,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    LevelAndFrequencyRow(
                        level = sense.learnerLevel,
                        frequency = sense.frequency,
                        nameType = sense.nameType,
                        pos = state.pos
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.showNavigationArrow) {
                        IconButton(
                            onClick = onNavigateToDetail,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = ArrowForwardVector,
                                contentDescription = "Navigate to word detail",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = if (state.favorite) "❤️" else "🤍",
                            style = MaterialTheme.typography.titleMedium
                        )
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
                        .padding(horizontal = 16.dp, vertical = 0.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (sense.targetLangDefinitions.isNotEmpty()) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (translationBasedHeader != null) {
                                Text(
                                    text = sense.senseDefinition,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (translationHeaderSuffix == null) {
                                sense.targetLangDefinitions.forEach {
                                    Text(
                                        text = it.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (sense.traits.isNotEmpty()) {
                        TraitsList(traits = sense.traits)
                    }

                    if (sense.examples.isNotEmpty()) {
                        val examplePreview = sense.examples.first().text
                            .replace("\n", " ")
                        ExpandableSection(
                            title = "Examples",
                            expanded = state.examplesExpanded,
                            onToggle = onExamplesToggle,
                            supportingText = examplePreview.ifBlank { null },
                            headlineStyle = MaterialTheme.typography.titleSmall
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                sense.examples.forEach { ex ->
                                    BulletHighlightedText(
                                        text = ex.text,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (ex.targetLangTranslations.isNotEmpty()) {
                                        ex.targetLangTranslations.forEach { (_, translation) ->
                                            PrefixedHighlightedText(
                                                prefix = "– ",
                                                text = translation,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 24.dp)
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
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    EntryList(
                        "Synonyms",
                        sense.synonyms,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    EntryList(
                        "Antonyms",
                        sense.antonyms,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )

                    val languageOrder = buildList {
                        sense.targetLangDefinitions.keys.forEach { add(it) }
                        sense.translations.keys.forEach { if (!contains(it)) add(it) }
                    }

                    languageOrder.forEach { lang ->
                        if (sense.translations[lang]?.isNotEmpty() ?: false) {
                            val langExpanded = state.languageExpanded[lang] ?: expanded
                            LanguageSection(
                                languageCode = lang,
                                sense = sense,
                                expanded = langExpanded,
                                onToggle = { onLanguageToggle(lang) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageSection(
    languageCode: String,
    sense: LanguageCardResponseSense,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val languageLabel = codeToLanguage.getOrElse(languageCode) { languageCode }
    val translations = sense.translations[languageCode].orEmpty()

    ExpandableSection(
        title = languageLabel,
        expanded = expanded,
        onToggle = onToggle,
        supportingText = null,
        headlineStyle = MaterialTheme.typography.titleSmall
    ) {
        TranslationList(translations = translations)
    }
}

@Composable
private fun TranslationList(translations: List<LanguageCardTranslation>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        translations.forEach { translation ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BulletHighlightedText(
                    text = translation.targetLangWord,
                    style = MaterialTheme.typography.bodyMedium
                )
                translation.targetLangSenseClarification?.takeIf { it.isNotBlank() }?.let { clarification ->
                    Text(
                        text = clarification,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp)
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
        SectionLabel("Notes")
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
                                fontWeight = FontWeight.Companion.SemiBold,
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
    pos: PartOfSpeech? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (lc, lcc) = colorsForLevel(level)
        val (fc, fcc) = colorsForFrequency(frequency)
        if (pos != null) {
            val (pc, pcc) = colorsForPos(pos)
            Badge(
                text = pos.name.lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                containerColor = pc,
                contentColor = pcc
            )
        }
        Badge(text = level.name, containerColor = lc, contentColor = lcc)
        Badge(text = frequency.label, containerColor = fc, contentColor = fcc)
        if (nameType != null && nameType != NameType.NO) {
            val (nc, ncc) = colorsForNameType(nameType)
            Badge(text = nameType.displayName, containerColor = nc, contentColor = ncc)
        }
    }
}

private fun translationsHeaderSuffix(
    sense: LanguageCardResponseSense,
    allSenses: List<LanguageCardResponseSense>
): String? {
    if (sense.translations.isEmpty()) {
        return null
    } else {
        val eachCount = allSenses.map { it.translationsHeader() }.groupingBy { it }.eachCount()
        val translationsHeader = sense.translationsHeader()
        return if (eachCount[translationsHeader] == 1) {
            null
        } else {
            sense.targetLangDefinitions.map { definition -> definition.value.replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() } }
                .joinToString(",")
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

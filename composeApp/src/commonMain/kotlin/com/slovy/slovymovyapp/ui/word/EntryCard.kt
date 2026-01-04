package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.LanguageCardForm
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.ui.components.AppCard
import com.slovy.slovymovyapp.ui.components.PartOfSpeechIndicator

@Composable
private fun FormsList(forms: List<LanguageCardForm>) {
    if (forms.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        forms.forEach { form ->
            val tags = if (form.tags.isNotEmpty()) {
                form.tags.joinToString(separator = ", ", prefix = " (", postfix = ")")
            } else {
                ""
            }
            HighlightedText(
                text = "${Typography.bullet} ${form.form}$tags",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
internal fun EntryCard(
    entry: LanguageCardPosEntry,
    entryState: EntryUiState,
    cardLoading: Boolean = false,
    cardError: String? = null,
    translationLoading: Boolean = false,
    translationError: String? = null,
    onEntryToggle: () -> Unit,
    onFormsToggle: () -> Unit,
    onSenseToggle: (String) -> Unit,
    onSensePositioned: (String, Float) -> Unit = { _, _ -> },
    onSenseFavoriteToggle: (String) -> Unit = {},
    relatedWords: Set<String> = emptySet(),
    onWordClick: (String) -> Unit = {}
) {
    val expanded = entryState.expanded && !cardLoading && cardError == null
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(onClick = onEntryToggle)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PartOfSpeechIndicator(
                    partOfSpeech = entry.pos.name,
                    cardLoading = cardLoading,
                    cardError = cardError
                )
                if (!cardLoading && cardError == null) {

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "(${entry.senses.size} meaning${pluralEnding(entry.senses)})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )


                    Spacer(modifier = Modifier.width(16.dp))

                    val summaryParts = buildList {
                        if (entry.forms.isNotEmpty() && !cardLoading) {
                            add("${entry.forms.size} form${pluralEnding(entry.forms)}")
                        }
                    }.joinToString(" ${Typography.bullet} ")

                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedVisibility(
                            visible = !expanded && summaryParts.isNotBlank(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HighlightedText(
                                    text = summaryParts,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                        contentDescription = if (expanded) {
                            "Collapse ${entry.pos} entry"
                        } else {
                            "Expand ${entry.pos} entry"
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
                        .padding(horizontal = 20.dp, vertical = 0.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {

                    if (entry.forms.isNotEmpty()) {
                        ExpandableSection(
                            title = "Grammar",
                            expanded = entryState.formsExpanded,
                            onToggle = onFormsToggle,
                            supportingText = null,
                            headlineStyle = MaterialTheme.typography.titleSmall
                        ) {
                            SectionLabel("Grammar")
                            FormsList(entry.forms)
                        }
                    }

                    val groupEntries = entry.senses.groupBy { it.semanticGroupId }.entries.toList()
                    groupEntries.forEachIndexed { groupIndex, (groupId, senseList) ->
                        val showGroup = groupEntries.size > 1 && groupIndex > 0 && senseList.size > 1
                        if (showGroup) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (showGroup) {
                                SectionLabel("Group: $groupId")
                            }

                            senseList.forEachIndexed { senseIndex, sense ->
                                val senseState = entryState.senses.find { it.senseId == sense.senseId }
                                    ?: throw IllegalStateException("Sense state not found for sense ${sense.senseId}")
                                SenseCard(
                                    data = SenseCardData(
                                        senseId = sense.senseId,
                                        sense = sense,
                                        pos = entry.pos,
                                        translationLoading = translationLoading || senseState.translationLoading,
                                        translationError = translationError ?: senseState.translationError
                                    ),
                                    state = senseState,
                                    onToggle = { onSenseToggle(sense.senseId) },
                                    onPositioned = onSensePositioned,
                                    onFavoriteToggle = { onSenseFavoriteToggle(sense.senseId) },
                                    relatedWords = relatedWords,
                                    onWordClick = onWordClick
                                )
                                if (senseIndex < senseList.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun pluralEnding(someList: List<*>): String = if (someList.size == 1) "" else "s"

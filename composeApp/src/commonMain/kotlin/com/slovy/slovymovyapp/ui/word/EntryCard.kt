package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.LanguageCardForm
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.ui.components.PartOfSpeechIndicator

@Composable
private fun FormsList(forms: List<LanguageCardForm>) {
    if (forms.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        forms.forEach { form ->
            val tags = if (form.tags.isNotEmpty()) {
                form.tags.joinToString(separator = ", ", prefix = " (", postfix = ")")
            } else {
                ""
            }
            HighlightedText(
                text = "${Typography.bullet} ${form.form}$tags",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun GrammarSection(
    forms: List<LanguageCardForm>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onToggle),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = shape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "${forms.size} form${if (forms.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                    contentDescription = if (expanded) "Hide forms" else "Show forms",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            ) {
                FormsList(forms)
            }
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
    relatedWords: Map<String, RelatedWord> = emptyMap(),
    onWordClick: (String) -> Unit = {},
    lemma: String
) {
    val expanded = entryState.expanded && !cardLoading && cardError == null
    Column(modifier = Modifier.fillMaxWidth()) {
        // POS Header - kept with its own padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onEntryToggle)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PartOfSpeechIndicator(
                partOfSpeech = entry.pos.name,
                meaningCount = if (!cardLoading && cardError == null) entry.senses.size else null,
                cardLoading = cardLoading,
                cardError = cardError
            )
            if (!cardLoading && cardError == null) {
                Spacer(modifier = Modifier.weight(1f))

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
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grammar section - indented under POS header
                if (entry.forms.isNotEmpty()) {
                    GrammarSection(
                        forms = entry.forms,
                        expanded = entryState.formsExpanded,
                        onToggle = onFormsToggle,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Senses - edge-to-edge (no horizontal padding)
                entry.senses.forEach { sense ->
                    val senseState = entryState.senses.find { it.senseId == sense.senseId }
                        ?: throw IllegalStateException("Sense state not found for sense ${sense.senseId}")
                    SenseCard(
                        data = SenseCardData(
                            lemma = lemma,
                            showLemma = false,
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
                }
            }
        }
    }
}

fun pluralEnding(someList: List<*>): String = if (someList.size == 1) "" else "s"

package com.slovy.slovymovyapp.ui.word

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.data.util.HtmlTagParser

/**
 * Returns the appropriate navigation arrow based on word availability.
 * @param isOnline true if word is online-only, false if available offline, null if unknown
 */
internal const val CLICKABLE_WORD_TAG_PREFIX = "CLICKABLE_WORD_"

internal fun navigationArrow(isOnline: Boolean?): String =
    if (isOnline == false) "➤" else "➼"

@Composable
internal fun SectionLabel(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        HighlightedText(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoadingPlaceholder(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ErrorPlaceholder(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ErrorIcon(Modifier.size(20.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun ErrorIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.Filled.ErrorOutline,
        contentDescription = "Error",
        modifier = modifier,
        tint = MaterialTheme.colorScheme.error
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryList(
    label: String,
    values: List<String>,
    containerColor: Color,
    contentColor: Color,
    relatedWords: Map<String, RelatedWord> = emptyMap(),
    onWordClick: (String) -> Unit = {},
    favoriteLemmas: Set<String> = emptySet()
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { raw ->
                val word = HtmlTagParser.stripTags(raw).trim()
                if (word.isBlank()) return@forEach
                val matchingKey = relatedWords.keys.firstOrNull { it.equals(word, ignoreCase = true) }
                val isClickable = matchingKey != null
                Badge(
                    text = word,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    isClickable = isClickable,
                    isOnline = matchingKey?.let { relatedWords[it]?.online },
                    isFavorite = favoriteLemmas.any { it.equals(word, ignoreCase = true) },
                    onClick = if (isClickable) {
                        { onWordClick(matchingKey!!) }
                    } else null
                )
            }
        }
    }
}

// Helpers to render <w>word</w> with special highlight style across all displayed text
@Composable
internal fun HighlightedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier.Companion,
    textAlign: TextAlign? = null,
    clickableWords: Set<String> = emptySet(),
    onWordClick: (String) -> Unit = {}
) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val clickableHighlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        textDecoration = TextDecoration.Underline
    )
    val annotated = buildAnnotatedString {
        appendTextWithW(this, text, highlight, clickableHighlight, clickableWords, onWordClick)
    }

    Text(
        text = annotated,
        style = style.merge(TextStyle(color = style.color)),
        modifier = modifier,
        textAlign = textAlign
    )
}

internal fun appendTextWithW(
    builder: AnnotatedString.Builder,
    input: String,
    highlight: SpanStyle,
    clickableHighlight: SpanStyle = highlight,
    clickableWords: Set<String> = emptySet(),
    onWordClick: (String) -> Unit = {}
) {
    val segments = HtmlTagParser.parseTextSegments(input)

    segments.forEach { segment ->
        if (segment.isTagged) {
            // This is a word inside <w> tags
            val word = segment.text
            val isClickable = clickableWords.any { it.equals(word, ignoreCase = true) }
            val styleToUse = if (isClickable) clickableHighlight else highlight

            if (isClickable) {
                // Use LinkAnnotation for clickable words
                val link = LinkAnnotation.Clickable(
                    tag = "$CLICKABLE_WORD_TAG_PREFIX$word",
                    linkInteractionListener = {
                        onWordClick(word)
                    }
                )
                builder.withLink(link) {
                    builder.withStyle(styleToUse) {
                        builder.append(word)
                    }
                }
            } else {
                builder.withStyle(styleToUse) {
                    builder.append(word)
                }
            }
        } else {
            // Regular text, no highlighting
            builder.append(segment.text)
        }
    }
}

@Composable
internal fun Badge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    shape: Shape = RoundedCornerShape(6.dp),
    isClickable: Boolean = false,
    isOnline: Boolean? = null,
    isFavorite: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = BorderStroke(
            width = if (isClickable) 0.75.dp else 0.5.dp,
            color = contentColor.copy(alpha = if (isClickable) 0.4f else 0.15f)
        ),
        tonalElevation = if (isClickable) 2.dp else 0.dp,
        shadowElevation = if (isClickable) 1.dp else 0.dp,
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else Modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (isFavorite) {
                Text(
                    text = "\u2665",
                    style = style,
                    color = Color(0xFFC46060)
                )
            }
            Text(
                text = text,
                style = style.copy(
                    fontWeight = if (isClickable) FontWeight.Medium else style.fontWeight
                )
            )
            if (isClickable) {
                Text(
                    text = navigationArrow(isOnline),
                    style = style.copy(fontWeight = FontWeight.Bold),
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
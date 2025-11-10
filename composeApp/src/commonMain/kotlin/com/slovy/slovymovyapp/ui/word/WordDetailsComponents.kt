package com.slovy.slovymovyapp.ui.word

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.slovy.slovymovyapp.data.util.HtmlTagParser

@Composable
internal fun SectionLabel(text: String) {
    HighlightedText(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryList(
    label: String,
    values: List<String>,
    containerColor: Color,
    contentColor: Color,
    clickableWords: Set<String> = emptySet(),
    onWordClick: (String) -> Unit = {}
) {
    if (values.isEmpty()) return
    SectionLabel(label)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { word ->
            val isClickable = clickableWords.any { it.equals(word, ignoreCase = true) }
            Badge(
                text = word,
                containerColor = containerColor,
                contentColor = contentColor,
                isClickable = isClickable,
                onClick = if (isClickable) {
                    { onWordClick(word) }
                } else null
            )
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

@Composable
internal fun BulletHighlightedText(
    text: String,
    style: TextStyle,
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
        append(Typography.bullet)
        appendTextWithW(this, text, highlight, clickableHighlight, clickableWords, onWordClick)
    }

    Text(
        text = annotated,
        style = style.merge(TextStyle(color = style.color))
    )
}

@Composable
internal fun PrefixedHighlightedText(
    prefix: String,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier.Companion,
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
        append(prefix)
        appendTextWithW(this, text, highlight, clickableHighlight, clickableWords, onWordClick)
    }

    Text(
        text = annotated,
        style = style.merge(TextStyle(color = style.color)),
        modifier = modifier
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
                    tag = "CLICKABLE_WORD_$word",
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
    shape: Shape = RoundedCornerShape(12.dp),
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = if (isClickable) {
            BorderStroke(1.5.dp, contentColor.copy(alpha = 0.4f))
        } else null,
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
            Text(
                text = text,
                style = style.copy(
                    fontWeight = if (isClickable) FontWeight.Medium else style.fontWeight
                )
            )
            if (isClickable) {
                Text(
                    text = "→",
                    style = style.copy(fontWeight = FontWeight.Bold),
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
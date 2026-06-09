package com.slovy.slovymovyapp.ui.word

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository.Companion.normalizeLemma
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

/**
 * Returns the appropriate navigation arrow based on word availability.
 * @param isOnline true if word is online-only, false if available offline, null if unknown
 */
internal const val CLICKABLE_WORD_TAG_PREFIX = "CLICKABLE_WORD_"

internal fun navigationArrow(isOnline: Boolean?): String =
    if (isOnline == false) "›" else "››"

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
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
        contentDescription = stringResource(Res.string.common_error),
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
    favoriteLemmas: Set<String> = emptySet(),
    chipShape: Shape = RoundedCornerShape(14.dp),
    chipBorder: BorderStroke? = null,
    chipSpacing: Dp = 6.dp
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(chipSpacing),
            verticalArrangement = Arrangement.spacedBy(chipSpacing)
        ) {
            values.forEach { word ->
                val matchingKey = relatedWords.keys.firstOrNull { it.equals(word, ignoreCase = true) }
                val resolvedLemma = matchingKey?.let { relatedWords[it]?.lemma } ?: word
                val isClickable = matchingKey != null
                Badge(
                    text = word,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    shape = chipShape,
                    isClickable = isClickable,
                    isOnline = matchingKey?.let { relatedWords[it]?.online },
                    isFavorite = normalizeLemma(resolvedLemma) in favoriteLemmas,
                    border = chipBorder ?: if (isClickable) BorderStroke(0.5.dp, contentColor.copy(alpha = 0.18f)) else null,
                    onClick = if (isClickable) {
                        { onWordClick(resolvedLemma) }
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
    val annotated = remember(text, clickableWords, onWordClick, highlight, clickableHighlight) {
        buildAnnotatedString {
            appendTextWithW(this, text, highlight, clickableHighlight, clickableWords, onWordClick)
        }
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
            val word = segment.text
            val isClickable = clickableWords.any { it.equals(word, ignoreCase = true) }
            val styleToUse = if (isClickable) clickableHighlight else highlight

            if (isClickable) {
                val link = LinkAnnotation.Clickable(
                    tag = "$CLICKABLE_WORD_TAG_PREFIX$word",
                    linkInteractionListener = { onWordClick(word) }
                )
                builder.withLink(link) {
                    builder.withStyle(styleToUse) { builder.append(word) }
                }
            } else {
                builder.withStyle(styleToUse) { builder.append(word) }
            }
        } else {
            builder.append(segment.text)
        }
    }
}

@Composable
internal fun Badge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    style: TextStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = MaterialTheme.serifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    ),
    shape: Shape = RoundedCornerShape(14.dp),
    isClickable: Boolean = false,
    isOnline: Boolean? = null,
    isFavorite: Boolean = false,
    border: BorderStroke? = if (isClickable) BorderStroke(0.5.dp, contentColor.copy(alpha = 0.18f)) else null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = border,
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else Modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = if (isClickable && isFavorite) 9.dp else if (isClickable) 11.dp else 12.dp,
                end = if (isClickable) 8.dp else 12.dp,
                top = 5.dp,
                bottom = 5.dp
            )
        ) {
            if (isFavorite) {
                Text(
                    text = "\u2665",
                    style = style.copy(fontSize = 11.sp, fontFamily = FontFamily.Default),
                    color = FavoriteAccentColor
                )
            }
            Text(
                text = text,
                style = style
            )
            if (isClickable) {
                Text(
                    text = navigationArrow(isOnline),
                    style = style.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
internal fun MetaBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp,
                lineHeight = 14.sp
            ),
            color = contentColor
        )
    }
}

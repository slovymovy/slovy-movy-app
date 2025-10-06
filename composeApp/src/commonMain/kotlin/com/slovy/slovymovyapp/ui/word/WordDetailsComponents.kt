package com.slovy.slovymovyapp.ui.word

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    label: String, values: List<String>, containerColor: Color, contentColor: Color
) {
    if (values.isEmpty()) return
    SectionLabel(label)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach {
            Badge(
                text = it,
                containerColor = containerColor,
                contentColor = contentColor
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
    textAlign: TextAlign? = null
) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val annotated = buildAnnotatedString {
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style, modifier = modifier, textAlign = textAlign)
}

@Composable
internal fun BulletHighlightedText(text: String, style: TextStyle) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val annotated = buildAnnotatedString {
        append(Typography.bullet)
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style)
}

@Composable
internal fun PrefixedHighlightedText(
    prefix: String,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier.Companion
) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val annotated = buildAnnotatedString {
        append(prefix)
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style, modifier = modifier)
}

internal fun appendTextWithW(builder: AnnotatedString.Builder, input: String, highlight: SpanStyle) {
    var i = 0
    while (i < input.length) {
        val start = input.indexOf("<w>", i)
        if (start == -1) {
            builder.append(input.substring(i))
            break
        }
        // Append text before <w>
        if (start > i) builder.append(input.substring(i, start))
        val end = input.indexOf("</w>", start + 3)
        if (end == -1) {
            // No closing tag – append the rest verbatim
            builder.append(input.substring(start))
            break
        }
        val word = input.substring(start + 3, end)
        builder.withStyle(highlight) { builder.append(word) }
        i = end + 4 // move after </w>
    }
}

@Composable
internal fun Badge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    style: TextStyle = MaterialTheme.typography.labelMedium
) {
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = text,
            style = style,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
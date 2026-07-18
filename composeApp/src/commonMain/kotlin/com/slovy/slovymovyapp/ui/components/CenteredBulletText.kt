package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em

// Bullet glyphs are shaped with the text run that follows them, so a font-fallback boundary (e.g.
// a Cyrillic line after a Latin one) can render dots of different sizes. These helpers replace
// every bullet with inline content so the dot is drawn as a real circle: identical on every line,
// sized relative to the text, and centered by the text engine. Every screen that renders the
// bullet-per-language blocks (study cards, word details) must use both halves together: append via
// appendWithCenteredBullets / appendWithMutedCenteredBullets and pass centeredBulletInlineContent
// to the Text call. The muted variant exists because inline content cannot inherit span colors —
// a bullet inside a muted span (e.g. a clarification) must be routed to a separately colored dot.

private const val CenteredBulletInlineId = "centered_bullet"
private const val MutedCenteredBulletInlineId = "centered_bullet_muted"

fun appendWithCenteredBullets(builder: AnnotatedString.Builder, text: String) {
    appendWithCenteredBullets(builder, text, CenteredBulletInlineId)
}

fun appendWithMutedCenteredBullets(builder: AnnotatedString.Builder, text: String) {
    appendWithCenteredBullets(builder, text, MutedCenteredBulletInlineId)
}

private fun appendWithCenteredBullets(
    builder: AnnotatedString.Builder,
    text: String,
    inlineContentId: String,
) {
    var start = 0
    while (start < text.length) {
        val bulletIndex = text.indexOf(Typography.bullet, start)
        if (bulletIndex < 0) {
            builder.append(text.substring(start))
            return
        }
        builder.append(text.substring(start, bulletIndex))
        builder.appendInlineContent(inlineContentId, alternateText = Typography.bullet.toString())
        start = bulletIndex + 1
    }
}

fun centeredBulletInlineContent(color: Color): Map<String, InlineTextContent> =
    mapOf(CenteredBulletInlineId to centeredBulletCircle(color))

fun centeredBulletInlineContent(color: Color, mutedColor: Color): Map<String, InlineTextContent> =
    mapOf(
        CenteredBulletInlineId to centeredBulletCircle(color),
        MutedCenteredBulletInlineId to centeredBulletCircle(mutedColor),
    )

private fun centeredBulletCircle(color: Color): InlineTextContent =
    InlineTextContent(
        Placeholder(
            width = 0.35.em,
            height = 0.35.em,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(color = color, shape = CircleShape))
    }

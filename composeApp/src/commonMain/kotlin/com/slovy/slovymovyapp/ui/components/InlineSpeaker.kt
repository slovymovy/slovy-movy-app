package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.slovy.slovymovyapp.speech.AudioControl
import com.slovy.slovymovyapp.speech.RowAudioPhase
import com.slovy.slovymovyapp.ui.SpeakerVector

/**
 * The app's inline speaker: a glyph bound to the end of a text run, used wherever a specific piece
 * of text can be spoken (a lemma, an example sentence) as opposed to a card-level control.
 *
 * It lives here rather than beside one screen because the same glyph, size, tint and hit box have
 * to appear identically on the sense cards and in a study session — a second, hand-built speaker
 * next to an example is immediately visible as a different control.
 */

/** Id of the inline placeholder that hosts the speaker glyph. */
const val SpeakerInlineId: String = "inline_speaker"

/** Glyph size for a speaker on a headline-sized word (titleMedium). */
val LemmaSpeakerGlyphSize: Dp = 18.dp

/** Glyph size for a speaker on an example sentence (bodyLarge). */
val ExampleSpeakerGlyphSize: Dp = 16.dp

/**
 * Appends the trailing space and placeholder that host an inline speaker at the end of a text run.
 * The normal space lets the glyph wrap onto the next line with the text rather than overflow.
 */
fun appendSpeakerPlaceholder(builder: AnnotatedString.Builder) {
    builder.append(' ')
    builder.appendInlineContent(SpeakerInlineId, "🔊")
}

/**
 * Inline content for the speaker glyph. The glyph is a placeholder so it stays inside its text's
 * run and never detaches or truncates; the 44dp tap target is grown past the placeholder with
 * [Modifier.requiredSize] so it never adds line height. Callers differ only in [glyphSize] and the
 * two labels, which stay per-feature so each surface keeps its own copy.
 *
 * Merge the result with any other inline content the same text uses (bullets, for instance) rather
 * than replacing it.
 */
@Composable
fun speakerInlineContent(
    control: AudioControl,
    glyphSize: Dp,
    playLabel: String,
    stopLabel: String,
): Map<String, InlineTextContent> {
    val playing = control.phase == RowAudioPhase.PLAYING
    // Secondary ink, dialled back so the word stays the hero (design review #3). Theme-aware in
    // both light/dark via onSurfaceVariant.
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    return mapOf(
        SpeakerInlineId to InlineTextContent(
            // Width kept close to the glyph so the text-to-glyph gap reads tight (design review #4);
            // height tracks ~1em of the hosting text. The 44dp hit box overflows this slot, so the
            // placeholder only governs layout/spacing, never the tap target.
            Placeholder(
                width = 1.2.em,
                height = 1.1.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            )
        ) {
            // Outer box fills the placeholder slot; the inner 44dp target overflows symmetrically
            // (centred) so the glyph stays aligned to the text while the hit box extends out.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .requiredSize(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClick = control.onToggle,
                            role = Role.Button,
                            onClickLabel = if (playing) stopLabel else playLabel,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        control.phase == RowAudioPhase.PREPARING -> SpinningProgressIndicator(
                            modifier = Modifier.size(glyphSize - 2.dp),
                            strokeWidth = 2.dp,
                            color = tint,
                        )

                        else -> Icon(
                            imageVector = if (playing) Icons.Filled.StopCircle else SpeakerVector,
                            contentDescription = if (playing) stopLabel else playLabel,
                            tint = tint,
                            // Nudge the glyph to the text's optical midline (x-height centre), which
                            // reads slightly below the line centre (design review #2). Visual only —
                            // the hit box is unmoved.
                            modifier = Modifier.size(glyphSize).offset(y = 1.dp),
                        )
                    }
                }
            }
        }
    )
}

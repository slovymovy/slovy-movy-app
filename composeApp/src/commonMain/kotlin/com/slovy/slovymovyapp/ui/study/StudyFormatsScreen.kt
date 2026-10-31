package com.slovy.slovymovyapp.ui.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.learning.StudyFormat
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.icons.StudyFormatFillTheGap
import com.slovy.slovymovyapp.ui.icons.StudyFormatListen
import com.slovy.slovymovyapp.ui.icons.StudyFormatSpelling
import com.slovy.slovymovyapp.ui.icons.StudyFormatTranslationToWord
import com.slovy.slovymovyapp.ui.icons.StudyFormatWordToTranslation
import com.slovy.slovymovyapp.ui.icons.ThinkingOtter
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.study_explainer_done
import slovymovyapp.composeapp.generated.resources.study_explainer_title

/** The formats page opened from Settings for one learning language. */
@Composable
fun StudyFormatsScreen(
    viewModel: StudyFormatsViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(viewModel.closed) {
        if (viewModel.closed) onBack()
    }
    val state = viewModel.state ?: return
    StudyFormatsContent(
        state = state,
        scrollState = viewModel.scrollState,
        onToggle = viewModel::toggle,
        onDismiss = onBack,
        modifier = Modifier.fillMaxSize(),
    )
}

// The page's own measures, from the "How you'll practice" design handoff.
private val PageSidePadding = 20.dp
private val NodeSize = 28.dp
private val RailWidth = 2.dp
private val GlyphSize = 15.dp
private val ControlWidth = 52.dp
private val RowMinHeight = 62.dp
private const val INACTIVE_ALPHA = 0.5f
private const val FADE_MILLIS = 150

/**
 * The "How you'll practice" page. In a session it covers the whole screen and leaves through its
 * one button, so the card underneath stays exactly as the learner left it.
 *
 * The six formats are one ordered path: a node per row joined by a rail. Only the first and the
 * last row carry a switch; the rows between them only explain. Switching an end off dims its
 * text and node and breaks the rail beside it, while the switch stays fully opaque so it remains
 * clearly operable. Nothing is written under the list: the row's change is the whole message.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StudyFormatsContent(
    state: StudyFormatsUiState,
    scrollState: ScrollState,
    onToggle: (StudyFormat) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The page covers the session, so the system back gesture must close the page, not the
    // session behind it.
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = PageSidePadding),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(AppSpacing.md))
                    Image(
                        imageVector = SlovyIcons.ThinkingOtter,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        text = stringResource(Res.string.study_explainer_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontSize = 25.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        text = state.intro.resolve(),
                        // The handoff's reading font is the serif, as under the onboarding headers.
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontStyle = MaterialTheme.uiItalic,
                            fontSize = 13.5.sp,
                            lineHeight = 19.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = AppSpacing.sm),
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { collectionInfo = CollectionInfo(rowCount = state.rows.size, columnCount = 1) },
                ) {
                    state.rows.forEachIndexed { index, row ->
                        StudyFormatRowItem(
                            row = row,
                            index = index,
                            // Null at the ends: the path has no rail before its first or after
                            // its last step.
                            previousEnabled = state.rows.getOrNull(index - 1)?.enabled,
                            nextEnabled = state.rows.getOrNull(index + 1)?.enabled,
                            languageCode = state.languageCode,
                            onToggle = onToggle,
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.md))
            }
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PageSidePadding, end = PageSidePadding, top = AppSpacing.sm, bottom = AppSpacing.lg)
                    .height(52.dp),
            ) {
                Text(
                    text = stringResource(Res.string.study_explainer_done),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyFormatRowItem(
    row: StudyFormatRowUiState,
    index: Int,
    previousEnabled: Boolean?,
    nextEnabled: Boolean?,
    languageCode: String,
    onToggle: (StudyFormat) -> Unit,
) {
    val format = row.row.format
    // The dimming is a short fade; a platform that collapses animations shows the end state at
    // once, which is what reduced motion asks for.
    val contentAlpha by animateFloatAsState(
        targetValue = if (row.enabled) 1f else INACTIVE_ALPHA,
        animationSpec = tween(FADE_MILLIS),
    )
    val name = row.title.resolve()
    val label = row.label?.resolve()
    // A switchable row is the switch: one target for a finger and one node for a screen reader,
    // read as the format's name with its tag and its on/off state. An always-on row is one node
    // of plain text with no role.
    val rowSemantics = if (format != null) {
        Modifier
            .toggleable(
                value = row.enabled,
                role = Role.Switch,
                onValueChange = { onToggle(format) },
            )
            .semantics { contentDescription = listOfNotNull(name, label).joinToString(", ") }
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    // The row takes the height of its text and the node column stretches to match it: the rail
    // must span the whole row, and an intrinsic-height row would clip a tag that wrapped.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }
            .then(rowSemantics)
            .heightIn(min = RowMinHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(start = NodeSize + AppSpacing.md, top = AppSpacing.sm, bottom = AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm + AppSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            ) {
                // Name and tag flow: the tag sits after the name and drops under it when the
                // name is long or the font scale is high.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.5.sp,
                            lineHeight = 21.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (label != null) DifficultyTag(label)
                }
                Text(
                    text = row.description.resolve(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 17.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (format != null) {
                Switch(
                    checked = row.enabled,
                    // The row carries the semantics and the gesture; the switch only draws the
                    // state, at full opacity even when off, so it stays clearly operable.
                    onCheckedChange = null,
                )
            } else {
                // Keeps the text column of an always-on row in line with the switch rows.
                Spacer(Modifier.width(ControlWidth))
            }
        }
        PathNode(
            row = row,
            railAbove = previousEnabled?.let { it && row.enabled },
            railBelow = nextEnabled?.let { it && row.enabled },
            glyphAlpha = contentAlpha,
            languageCode = languageCode,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * One tag colour for both ends, tertiary mixed into the surface, with the surface's own text on
 * it: the handoff measured the tertiary container pair below 4.5:1 in the dark theme.
 */
@Composable
private fun DifficultyTag(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f).compositeOver(MaterialTheme.colorScheme.surface))
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
    )
}

/**
 * The row's step on the path: a circle with the format's glyph, and the rail to its neighbours.
 * A rail is solid when both rows it joins are on, dotted when either is off, and absent at the
 * ends of the path ([railAbove] / [railBelow] null). Decorative throughout.
 */
@Composable
private fun PathNode(
    row: StudyFormatRowUiState,
    railAbove: Boolean?,
    railBelow: Boolean?,
    glyphAlpha: Float,
    languageCode: String,
    modifier: Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val railColor = onSurface.copy(alpha = 0.18f)
    val borderColor = if (row.enabled) onSurface.copy(alpha = 0.22f) else railColor
    val fill = if (row.enabled) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .width(NodeSize)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Rail(solid = railAbove, color = railColor, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(NodeSize)
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, borderColor, CircleShape)
                .alpha(glyphAlpha),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = row.row.icon()
            if (glyph != null) {
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(GlyphSize),
                )
            } else {
                Text(
                    text = languageCode.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        lineHeight = 10.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
            Rail(solid = railBelow, color = railColor, modifier = Modifier.weight(1f))
        }
    }
}

/** A stretch of the path's rail; nothing is drawn when [solid] is null. */
@Composable
private fun Rail(solid: Boolean?, color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .width(RailWidth)
            .drawBehind {
                if (solid == null) return@drawBehind
                val dotted = PathEffect.dashPathEffect(floatArrayOf(size.width, size.width * 2))
                drawLine(
                    color = color,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = size.width,
                    // Flat ends let the solid stretches of neighbouring rows meet seamlessly;
                    // the dots are round.
                    cap = if (solid) StrokeCap.Butt else StrokeCap.Round,
                    pathEffect = if (solid) null else dotted,
                )
            },
    )
}

// Strokes of one weight, so the glyphs sit as a set. The source-only row shows the language's
// code instead of an icon.
private fun StudyFormatRow.icon(): ImageVector? = when (this) {
    StudyFormatRow.WORD_TO_TRANSLATION -> SlovyIcons.StudyFormatWordToTranslation
    StudyFormatRow.TRANSLATION_TO_WORD -> SlovyIcons.StudyFormatTranslationToWord
    StudyFormatRow.FILL_THE_GAP -> SlovyIcons.StudyFormatFillTheGap
    StudyFormatRow.SPELLING -> SlovyIcons.StudyFormatSpelling
    StudyFormatRow.LISTEN -> SlovyIcons.StudyFormatListen
    StudyFormatRow.SOURCE_ONLY -> null
}

private fun previewFormats(): StudyFormatsUiState =
    StudyFormatsUiState(
        intro = UiText.Plain("Formats get harder as each word sticks. Skip the first or last step if you like."),
        languageCode = "nl",
        rows = listOf(
            StudyFormatRowUiState(
                row = StudyFormatRow.WORD_TO_TRANSLATION,
                title = UiText.Plain("Word → translation"),
                description = UiText.Plain("See the word, recall the translation."),
                label = UiText.Plain("Easiest"),
                enabled = true,
            ),
            StudyFormatRowUiState(
                row = StudyFormatRow.TRANSLATION_TO_WORD,
                title = UiText.Plain("Translation → word"),
                description = UiText.Plain("See the translation, recall the word."),
                label = null,
                enabled = true,
            ),
            StudyFormatRowUiState(
                row = StudyFormatRow.FILL_THE_GAP,
                title = UiText.Plain("Fill the gap"),
                description = UiText.Plain("Complete the sentence."),
                label = null,
                enabled = true,
            ),
            StudyFormatRowUiState(
                row = StudyFormatRow.SPELLING,
                title = UiText.Plain("Spelling"),
                description = UiText.Plain("Type the word from its translation."),
                label = null,
                enabled = true,
            ),
            StudyFormatRowUiState(
                row = StudyFormatRow.LISTEN,
                title = UiText.Plain("Listen"),
                description = UiText.Plain("Hear it, no text shown."),
                label = null,
                enabled = true,
            ),
            StudyFormatRowUiState(
                row = StudyFormatRow.SOURCE_ONLY,
                title = UiText.Plain("Nederlands only"),
                description = UiText.Plain("Explained with no translation."),
                label = UiText.Plain("Hardest"),
                enabled = false,
            ),
        ),
    )

private fun previewBothFormatsOff(): StudyFormatsUiState =
    previewFormats().let { page ->
        page.copy(rows = page.rows.map { row -> row.copy(enabled = row.row.format == null) })
    }

@Preview
@Composable
private fun StudyFormatsPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudyFormatsContent(
            state = previewFormats(),
            scrollState = ScrollState(0),
            onToggle = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun StudyFormatsBothOffPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudyFormatsContent(
            state = previewBothFormatsOff(),
            scrollState = ScrollState(0),
            onToggle = {},
            onDismiss = {},
        )
    }
}

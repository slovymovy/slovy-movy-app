package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
import com.slovy.slovymovyapp.ui.icons.StudyFormatSourceOnly
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

/**
 * The "How studying works" page. In a session it covers the whole screen and leaves through its
 * one button, so the card underneath stays exactly as the learner left it.
 *
 * The formats are a flat, edge-to-edge list under hairlines, each with its switch. Copper stays
 * with the button, so nothing in the list shares a hue with a control.
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
                    .verticalScroll(scrollState),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(AppSpacing.xl))
                    Image(
                        imageVector = SlovyIcons.ThinkingOtter,
                        contentDescription = null,
                        modifier = Modifier.size(104.dp),
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    Text(
                        text = stringResource(Res.string.study_explainer_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = MaterialTheme.serifFontFamily,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = state.intro.resolve(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontStyle = MaterialTheme.uiItalic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        // A narrow measure so the line breaks near the middle of the sentence
                        // rather than orphaning its last two words.
                        modifier = Modifier.widthIn(max = 236.dp),
                    )
                    Spacer(Modifier.height(AppSpacing.xl))
                }
                state.rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = Dp.Hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    StudyFormatRow(row = row, onToggle = { onToggle(row.format) })
                }
                Spacer(Modifier.height(AppSpacing.lg))
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.lg)
                    .height(52.dp),
            ) {
                Text(
                    text = stringResource(Res.string.study_explainer_done),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyFormatRow(
    row: StudyFormatRowUiState,
    onToggle: () -> Unit,
) {
    val contentAlpha = if (row.enabled) 1f else 0.6f
    // The whole row is the switch: one target for a finger and one node for a screen reader,
    // read as the format's name with its on/off state.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = row.enabled,
                enabled = row.canDisable,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.mdPlus),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.mdPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = row.format.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .alpha(contentAlpha),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            // Title and tag flow: the tag sits after the title and drops to its own line when
            // the title is long or the font scale is high. The title keeps the full width to wrap
            // in, so it never breaks mid-word to make room for the tag.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.title.resolve(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                row.label?.let { label ->
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    ) {
                        Text(
                            text = label.resolve(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                        )
                    }
                }
            }
            Text(
                text = row.description.resolve(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = row.enabled,
            // The row carries the semantics and the gesture; the switch only draws the state.
            onCheckedChange = null,
            enabled = row.canDisable,
        )
    }
}

// Strokes of one weight, so the five sit as a set: Material's arrows are filled glyphs and the
// app's speaker is a solid silhouette, and mixed with a line circle they read as three weights.
private fun StudyFormat.icon(): ImageVector = when (this) {
    StudyFormat.WORD_TO_TRANSLATION -> SlovyIcons.StudyFormatWordToTranslation
    StudyFormat.TRANSLATION_TO_WORD -> SlovyIcons.StudyFormatTranslationToWord
    StudyFormat.FILL_THE_GAP -> SlovyIcons.StudyFormatFillTheGap
    StudyFormat.LISTEN -> SlovyIcons.StudyFormatListen
    StudyFormat.SOURCE_ONLY -> SlovyIcons.StudyFormatSourceOnly
}

private fun previewFormats(): StudyFormatsUiState =
    StudyFormatsUiState(
        intro = UiText.Plain("As a word sticks, harder formats unlock on their own."),
        rows = listOf(
            StudyFormatRowUiState(
                format = StudyFormat.WORD_TO_TRANSLATION,
                title = UiText.Plain("Word → translation"),
                description = UiText.Plain("See the word, recall the translation."),
                label = UiText.Plain("Easiest"),
                enabled = true,
                canDisable = true,
            ),
            StudyFormatRowUiState(
                format = StudyFormat.TRANSLATION_TO_WORD,
                title = UiText.Plain("Translation → word"),
                description = UiText.Plain("See the translation, recall the word."),
                label = null,
                enabled = true,
                canDisable = true,
            ),
            StudyFormatRowUiState(
                format = StudyFormat.FILL_THE_GAP,
                title = UiText.Plain("Fill the gap"),
                description = UiText.Plain("Complete the sentence."),
                label = null,
                enabled = true,
                canDisable = true,
            ),
            StudyFormatRowUiState(
                format = StudyFormat.LISTEN,
                title = UiText.Plain("Listen"),
                description = UiText.Plain("Hear it, no text shown."),
                label = null,
                enabled = false,
                canDisable = true,
            ),
            StudyFormatRowUiState(
                format = StudyFormat.SOURCE_ONLY,
                title = UiText.Plain("Nederlands only"),
                description = UiText.Plain("Explained with no translation."),
                label = UiText.Plain("Not for beginners"),
                enabled = false,
                canDisable = true,
            ),
        ),
    )

private fun previewSingleFormat(): StudyFormatsUiState =
    previewFormats().let { page ->
        page.copy(
            rows = page.rows.map { row ->
                val enabled = row.format == StudyFormat.WORD_TO_TRANSLATION
                row.copy(enabled = enabled, canDisable = !enabled)
            },
        )
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
private fun StudyFormatsLastEnabledPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudyFormatsContent(
            state = previewSingleFormat(),
            scrollState = ScrollState(0),
            onToggle = {},
            onDismiss = {},
        )
    }
}

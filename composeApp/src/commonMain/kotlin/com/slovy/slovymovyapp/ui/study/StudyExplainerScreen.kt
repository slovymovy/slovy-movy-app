package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.icons.ThinkingOtter
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.study_explainer_done
import slovymovyapp.composeapp.generated.resources.study_explainer_status_current
import slovymovyapp.composeapp.generated.resources.study_explainer_status_locked
import slovymovyapp.composeapp.generated.resources.study_explainer_status_unlocked
import slovymovyapp.composeapp.generated.resources.study_explainer_title

/**
 * The "How studying works" page. It covers the whole session while open and leaves through its
 * one button, so the card underneath stays exactly as the learner left it.
 *
 * The formats are a flat, edge-to-edge list under hairlines; the current one sits on a tinted
 * band. Copper stays with the button, so nothing in the list shares a hue with a control.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StudyExplainerContent(
    state: StudyExplainerUiState,
    scrollState: ScrollState,
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
                    StudyExplainerRow(row = row)
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

private const val NO_BREAK_SPACE = '\u00A0'

@Composable
private fun StudyExplainerRow(row: StudyExplainerRowUiState) {
    val isCurrent = row.status == StudyExplainerRowStatus.CURRENT
    val accent = MaterialTheme.colorScheme.tertiary
    val statusLabel: String = when (row.status) {
        StudyExplainerRowStatus.CURRENT -> stringResource(Res.string.study_explainer_status_current)
        StudyExplainerRowStatus.UNLOCKED -> stringResource(Res.string.study_explainer_status_unlocked)
        StudyExplainerRowStatus.NOT_YET -> stringResource(Res.string.study_explainer_status_locked)
    }
    val statusColor = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    // Name and state share one line of text, so they sit on one baseline and wrap together when a
    // long name or a large font scale needs a second line.
    val headline = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
            ),
        ) {
            append(row.title.resolve())
        }
        append("  ")
        if (isCurrent) {
            // A text bullet, not an inline placeholder: only text binds to the no-break
            // space, so the dot moves to the next line together with its label.
            withStyle(SpanStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)) {
                append("\u2022")
            }
            append(NO_BREAK_SPACE)
        }
        withStyle(
            SpanStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.9.sp,
                color = statusColor,
            ),
        ) {
            // The label may move to its own line when the name is long or the font scale is
            // high, but it must move whole: "YOU'RE / HERE" split across lines reads as two
            // labels.
            append(statusLabel.toUpperCase(Locale.current).replace(' ', NO_BREAK_SPACE))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.mdPlus),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.mdPlus),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = row.format.icon(),
            contentDescription = null,
            tint = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(18.dp)
                .alpha(if (row.status == StudyExplainerRowStatus.NOT_YET) 0.45f else 1f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = row.description.resolve(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun StudyExplainerFormat.icon(): ImageVector = when (this) {
    StudyExplainerFormat.WORD_TO_TRANSLATION -> StudyExplainerIcons.WordToTranslation
    StudyExplainerFormat.TRANSLATION_TO_WORD -> StudyExplainerIcons.TranslationToWord
    StudyExplainerFormat.FILL_THE_GAP -> StudyExplainerIcons.FillTheGap
    StudyExplainerFormat.LISTEN -> StudyExplainerIcons.Listen
    StudyExplainerFormat.SOURCE_ONLY -> StudyExplainerIcons.SourceOnly
}

private fun previewExplainer(): StudyExplainerUiState =
    StudyExplainerUiState(
        intro = UiText.Plain("As a word sticks, harder formats unlock on their own."),
        rows = listOf(
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.WORD_TO_TRANSLATION,
                title = UiText.Plain("Word → translation"),
                description = UiText.Plain("See the word, recall the translation."),
                status = StudyExplainerRowStatus.CURRENT,
            ),
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.TRANSLATION_TO_WORD,
                title = UiText.Plain("Translation → word"),
                description = UiText.Plain("See the translation, recall the word."),
                status = StudyExplainerRowStatus.NOT_YET,
            ),
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.FILL_THE_GAP,
                title = UiText.Plain("Fill the gap"),
                description = UiText.Plain("Complete the sentence."),
                status = StudyExplainerRowStatus.NOT_YET,
            ),
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.LISTEN,
                title = UiText.Plain("Listen"),
                description = UiText.Plain("Hear it, no text shown."),
                status = StudyExplainerRowStatus.NOT_YET,
            ),
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.SOURCE_ONLY,
                title = UiText.Plain("Nederlands only"),
                description = UiText.Plain("Explained with no translation."),
                status = StudyExplainerRowStatus.NOT_YET,
            ),
        ),
    )

@Preview
@Composable
private fun StudyExplainerPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudyExplainerContent(
            state = previewExplainer(),
            scrollState = ScrollState(0),
            onDismiss = {},
        )
    }
}

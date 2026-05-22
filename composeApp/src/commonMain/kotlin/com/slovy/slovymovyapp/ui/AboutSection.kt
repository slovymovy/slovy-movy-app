package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.AppBuildConfig
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
fun AboutSection(
    buildConfig: AppBuildConfig,
    onSendFeedback: () -> Unit = {},
    onAcknowledgements: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    AboutSectionContent(
        buildConfig = buildConfig,
        reviewTarget = storeReviewTarget(),
        onReview = { uriHandler.openUri(it.reviewUrl()) },
        onSendFeedback = onSendFeedback,
        onAcknowledgements = onAcknowledgements
    )
}

@Composable
private fun AboutSectionContent(
    buildConfig: AppBuildConfig,
    reviewTarget: StoreReviewTarget?,
    onReview: (StoreReviewTarget) -> Unit,
    onSendFeedback: () -> Unit,
    onAcknowledgements: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (reviewTarget != null) {
                AboutItem(
                    icon = Icons.Outlined.StarBorder,
                    title = when (reviewTarget) {
                        StoreReviewTarget.GOOGLE_PLAY -> stringResource(Res.string.about_review_google_play_title)
                        StoreReviewTarget.APP_STORE -> stringResource(Res.string.about_review_app_store_title)
                    },
                    subtitle = stringResource(Res.string.about_review_subtitle),
                    onClick = { onReview(reviewTarget) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = AppSpacing.lg),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            AboutItem(
                icon = Icons.Outlined.Feedback,
                title = stringResource(Res.string.about_send_feedback_title),
                subtitle = stringResource(Res.string.about_send_feedback_subtitle),
                onClick = onSendFeedback
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppSpacing.lg),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            AboutItem(
                icon = Icons.Outlined.VolunteerActivism,
                title = stringResource(Res.string.about_acknowledgements_title),
                subtitle = stringResource(Res.string.about_acknowledgements_subtitle),
                onClick = onAcknowledgements
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppSpacing.lg),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            AboutItem(
                icon = Icons.Outlined.Info,
                title = stringResource(Res.string.about_version_title),
                subtitle = buildConfig.versionName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcknowledgementsBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AcknowledgementsBottomSheetContent()
    }
}

@Composable
fun AcknowledgementsBottomSheetContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg)
            .padding(bottom = AppSpacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        Text(
            text = stringResource(Res.string.acknowledgements_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        AcknowledgementItem(
            title = stringResource(Res.string.acknowledgements_vocab_data_title),
            body = stringResource(Res.string.acknowledgements_vocab_data_body),
            urlLabel = "wiktionary.org",
            url = "https://www.wiktionary.org"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AcknowledgementItem(
            title = stringResource(Res.string.acknowledgements_dictionary_extracts_title),
            body = stringResource(Res.string.acknowledgements_dictionary_extracts_body),
            urlLabel = "kaikki.org",
            url = "https://kaikki.org"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AcknowledgementItem(
            title = stringResource(Res.string.acknowledgements_application_code_title),
            body = stringResource(Res.string.acknowledgements_application_code_body),
            urlLabel = "github.com/slovymovy/slovy-movy-app",
            url = "https://github.com/slovymovy/slovy-movy-app"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AcknowledgementItem(
            title = stringResource(Res.string.acknowledgements_fsrs_title),
            body = stringResource(Res.string.acknowledgements_fsrs_body),
            urlLabel = "github.com/open-spaced-repetition/FSRS-Kotlin",
            url = "https://github.com/open-spaced-repetition/FSRS-Kotlin"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AcknowledgementItem(
            title = stringResource(Res.string.acknowledgements_words_sources_title),
            body = stringResource(Res.string.acknowledgements_words_sources_body),
            urlLabel = "github.com/slovymovy/words",
            url = "https://github.com/slovymovy/words"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            text = stringResource(Res.string.acknowledgements_thanks),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AcknowledgementItem(
    title: String,
    body: String,
    urlLabel: String,
    url: String
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = urlLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri(url) }
        )
    }
}

@Composable
fun AboutItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview
@Composable
private fun AboutSectionPreviewWithReview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AboutSectionContent(
            buildConfig = previewBuildConfig,
            reviewTarget = StoreReviewTarget.GOOGLE_PLAY,
            onReview = {},
            onSendFeedback = {},
            onAcknowledgements = {}
        )
    }
}

@Preview
@Composable
private fun AboutSectionPreviewWithoutReview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AboutSectionContent(
            buildConfig = previewBuildConfig,
            reviewTarget = null,
            onReview = {},
            onSendFeedback = {},
            onAcknowledgements = {}
        )
    }
}

@Preview
@Composable
private fun AcknowledgementsBottomSheetPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AcknowledgementsBottomSheetContent()
    }
}

private val previewBuildConfig = AppBuildConfig(
    versionName = "1.2.345",
    versionCode = 345,
    isDebug = false,
    applicationId = "com.slovy.slovymovyapp"
)

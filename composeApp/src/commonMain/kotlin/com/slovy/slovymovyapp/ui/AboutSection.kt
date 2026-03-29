package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
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

@Composable
fun AboutSection(
    buildConfig: AppBuildConfig,
    onSendFeedback: () -> Unit = {},
    onAcknowledgements: () -> Unit = {}
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
            AboutItem(
                icon = Icons.Outlined.Feedback,
                title = "Send us feedback",
                subtitle = "We'd love to hear from you",
                onClick = onSendFeedback
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppSpacing.lg),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            AboutItem(
                icon = Icons.Outlined.VolunteerActivism,
                title = "Acknowledgements",
                subtitle = "Data sources & credits",
                onClick = onAcknowledgements
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppSpacing.lg),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            AboutItem(
                icon = Icons.Outlined.Info,
                title = "Version",
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
            text = "Acknowledgements",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        AcknowledgementItem(
            title = "Vocabulary data",
            body = "Our word data is sourced from Wiktionary, the free multilingual dictionary built by volunteers worldwide.",
            urlLabel = "wiktionary.org",
            url = "https://www.wiktionary.org"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AcknowledgementItem(
            title = "Dictionary extracts",
            body = "We use pre-processed dictionary data from Kaikki.org, extracted from Wiktionary using the open-source wiktextract tool by Tatu Ylonen.",
            urlLabel = "kaikki.org",
            url = "https://kaikki.org"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AcknowledgementItem(
            title = "Application code",
            body = "Application code is available under CC-BY-SA-4.0 license.",
            urlLabel = "github.com/slovymovy/slovy-movy-app",
            url = "https://github.com/slovymovy/slovy-movy-app"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AcknowledgementItem(
            title = "Words sources",
            body = "All the words data is available under CC-BY-SA-4.0 license.",
            urlLabel = "github.com/slovymovy/words",
            url = "https://github.com/slovymovy/words"
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "We're grateful to the Wiktionary community and to Tatu Ylonen for making this data freely available.",
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
private fun AcknowledgementsBottomSheetPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AcknowledgementsBottomSheetContent()
    }
}

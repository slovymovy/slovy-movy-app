package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DownloadProgress
import com.slovy.slovymovyapp.ui.theme.AppSpacing

data class LearningLanguageUiState(
    val language: Language,
    val isExpanded: Boolean = false,
    val dictionarySizeBytes: Long,
    val translations: List<TranslationUiState> = emptyList()
)

data class TranslationUiState(
    val targetLanguage: Language,
    val isDownloaded: Boolean,
    val isDownloadable: Boolean,
    val sizeBytes: Long? = null
)

@Composable
fun LearningLanguageCard(
    state: LearningLanguageUiState,
    downloadingItems: Map<String, DownloadProgress?>,
    onToggleExpansion: () -> Unit,
    onRemove: () -> Unit,
    onDownloadTranslation: (Language) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteTranslation: (Language) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                MaterialTheme.shapes.extraLarge
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = if (state.isExpanded) "Expanded" else "Collapsed"
                    }
                    .clickable(
                        onClick = onToggleExpansion,
                        role = Role.Button,
                        onClickLabel = if (state.isExpanded) "Collapse" else "Expand"
                    )
                    .padding(AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${state.language.flag} ${state.language.selfName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = "Dictionary: ${formatFileSize(state.dictionarySizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.isExpanded && state.translations.isNotEmpty()) {
                        val downloadable = state.translations.filter { it.isDownloadable || it.isDownloaded }
                        val downloadedCount = downloadable.count { it.isDownloaded }
                        val downloadableCount = downloadable.size
                        val onlineOnlyCount = state.translations.size - downloadableCount
                        val downloadPart = when {
                            downloadableCount == 0 -> null
                            downloadedCount == downloadableCount -> "All $downloadableCount downloaded"
                            else -> "$downloadedCount of $downloadableCount downloaded"
                        }
                        val onlinePart = if (onlineOnlyCount > 0) "$onlineOnlyCount online only" else null
                        val summaryText = listOfNotNull(downloadPart, onlinePart).joinToString(", ")
                            .ifEmpty { "${state.translations.size} translations (online only)" }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove ${state.language.selfName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (state.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.isExpanded && state.translations.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg)
                        .padding(bottom = AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(
                        text = "Translations",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Downloaded first, then available, then online-only
                    val sorted = state.translations.sortedWith(
                        compareByDescending<TranslationUiState> { it.isDownloaded }
                            .thenByDescending { it.isDownloadable }
                            .thenBy { it.targetLanguage.ordinal }
                    )

                    sorted.forEach { translation ->
                        TranslationRow(
                            translation = translation,
                            sourceLanguage = state.language,
                            downloadingItems = downloadingItems,
                            onDownload = { onDownloadTranslation(translation.targetLanguage) },
                            onCancel = { key -> onCancelDownload(key) },
                            onDelete = { onDeleteTranslation(translation.targetLanguage) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationRow(
    translation: TranslationUiState,
    sourceLanguage: Language,
    downloadingItems: Map<String, DownloadProgress?>,
    onDownload: () -> Unit,
    onCancel: (String) -> Unit,
    onDelete: () -> Unit
) {
    val downloadKey = "trans_${sourceLanguage.code}_${translation.targetLanguage.code}"
    val isDownloading = downloadingItems.containsKey(downloadKey)
    val downloadProgress = downloadingItems[downloadKey]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (translation.isDownloaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        border = if (translation.isDownloaded) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${translation.targetLanguage.flag} ${translation.targetLanguage.selfName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                val statusText = when {
                    translation.isDownloaded && translation.sizeBytes != null ->
                        "Downloaded ${formatFileSize(translation.sizeBytes)}"
                    translation.isDownloaded -> "Downloaded"
                    isDownloading -> "Downloading..."
                    translation.isDownloadable && translation.sizeBytes != null ->
                        "Available ${formatFileSize(translation.sizeBytes)}"
                    translation.isDownloadable -> "Available"
                    else -> "Online only"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isDownloading -> {
                        CancellableProgressIndicator(
                            progress = downloadProgress?.percent?.toFloat()?.div(100f) ?: -1f,
                            onCancel = { onCancel(downloadKey) },
                            size = 48.dp
                        )
                    }
                    translation.isDownloaded -> {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete ${translation.targetLanguage.selfName} translation",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    translation.isDownloadable -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download ${translation.targetLanguage.selfName} translation",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Online only",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLanguageCard(
    language: Language,
    dictionarySizeBytes: Long?,
    downloadingItems: Map<String, DownloadProgress?>,
    onDownload: () -> Unit,
    onCancelDownload: (String) -> Unit
) {
    val downloadKey = "dict_${language.code}"
    val isDownloading = downloadingItems.containsKey(downloadKey)
    val downloadProgress = downloadingItems[downloadKey]

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${language.flag} ${language.selfName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (dictionarySizeBytes != null) {
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = "Dictionary ${formatFileSize(dictionarySizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading) {
                    CancellableProgressIndicator(
                        progress = downloadProgress?.percent?.toFloat()?.div(100f) ?: -1f,
                        onCancel = { onCancelDownload(downloadKey) },
                        size = 48.dp
                    )
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download ${language.selfName}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TranslationLanguageSection(
    allLanguages: List<Language>,
    selectedLanguages: Set<Language>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleLanguage: (Language) -> Unit
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
            if (!isExpanded) {
                // Collapsed: one language per row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            stateDescription = "Collapsed"
                        }
                        .clickable(
                            onClick = onToggleExpanded,
                            role = Role.Button,
                            onClickLabel = "Edit translation languages"
                        )
                        .padding(AppSpacing.lg)
                ) {
                    if (selectedLanguages.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No languages selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val sorted = selectedLanguages.sortedBy { it.ordinal }
                        sorted.forEachIndexed { index, language ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = AppSpacing.xs),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${language.flag} ${language.selfName}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (index == 0) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Expanded header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = "Expanded"
                        }
                        .clickable(
                            onClick = onToggleExpanded,
                            role = Role.Button,
                            onClickLabel = "Collapse"
                        )
                        .padding(AppSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg)
                        .padding(bottom = AppSpacing.lg)
                ) {
                    allLanguages.forEachIndexed { index, language ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = AppSpacing.xs),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        val isSelected = language in selectedLanguages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    stateDescription = if (isSelected) "Selected" else "Not selected"
                                }
                                .clickable(
                                    onClick = { onToggleLanguage(language) },
                                    role = Role.Checkbox,
                                    onClickLabel = "Toggle ${language.selfName} translation"
                                )
                                .padding(vertical = AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${language.flag} ${language.selfName}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )

                            CircularToggle(isSelected = isSelected)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LearningLanguageCardPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LearningLanguageCard(
            state = LearningLanguageUiState(
                language = Language.DUTCH,
                isExpanded = true,
                dictionarySizeBytes = 12 * 1024 * 1024L,
                translations = listOf(
                    TranslationUiState(
                        targetLanguage = Language.ENGLISH,
                        isDownloaded = true,
                        isDownloadable = true,
                        sizeBytes = 4 * 1024 * 1024L
                    ),
                    TranslationUiState(
                        targetLanguage = Language.RUSSIAN,
                        isDownloaded = false,
                        isDownloadable = false,
                        sizeBytes = null
                    ),
                    TranslationUiState(
                        targetLanguage = Language.POLISH,
                        isDownloaded = false,
                        isDownloadable = true,
                        sizeBytes = 3 * 1024 * 1024L
                    )
                )
            ),
            downloadingItems = emptyMap(),
            onToggleExpansion = {},
            onRemove = {},
            onDownloadTranslation = {},
            onCancelDownload = {},
            onDeleteTranslation = {}
        )
    }
}

@Preview
@Composable
private fun LearningLanguageCardCollapsedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LearningLanguageCard(
            state = LearningLanguageUiState(
                language = Language.DUTCH,
                isExpanded = false,
                dictionarySizeBytes = 12 * 1024 * 1024L,
                translations = listOf(
                    TranslationUiState(
                        targetLanguage = Language.ENGLISH,
                        isDownloaded = true,
                        isDownloadable = true,
                        sizeBytes = 4 * 1024 * 1024L
                    ),
                    TranslationUiState(
                        targetLanguage = Language.RUSSIAN,
                        isDownloaded = false,
                        isDownloadable = false
                    )
                )
            ),
            downloadingItems = emptyMap(),
            onToggleExpansion = {},
            onRemove = {},
            onDownloadTranslation = {},
            onCancelDownload = {},
            onDeleteTranslation = {}
        )
    }
}

@Preview
@Composable
private fun AddLanguageCardPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AddLanguageCard(
            language = Language.GERMAN,
            dictionarySizeBytes = 14 * 1024 * 1024L,
            downloadingItems = emptyMap(),
            onDownload = {},
            onCancelDownload = {}
        )
    }
}

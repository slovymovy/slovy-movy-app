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
import com.slovy.slovymovyapp.ui.word.pluralEnding
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

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
    val expandedStateDescription = stringResource(Res.string.common_state_expanded)
    val collapsedStateDescription = stringResource(Res.string.common_state_collapsed)
    val collapseAction = stringResource(Res.string.common_action_collapse)
    val expandAction = stringResource(Res.string.common_action_expand)

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
                        stateDescription = if (state.isExpanded) expandedStateDescription else collapsedStateDescription
                    }
                    .clickable(
                        onClick = onToggleExpansion,
                        role = Role.Button,
                        onClickLabel = if (state.isExpanded) collapseAction else expandAction
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
                        text = stringResource(
                            Res.string.settings_dictionary_size_with_colon,
                            formatFileSize(state.dictionarySizeBytes)
                        ),
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
                            downloadedCount == downloadableCount ->
                                stringResource(
                                    Res.string.settings_translations_all_downloaded,
                                    downloadableCount,
                                    pluralEnding(downloadableCount)
                                )
                            else -> stringResource(
                                Res.string.settings_translations_partially_downloaded,
                                downloadedCount,
                                downloadableCount,
                                pluralEnding(downloadableCount)
                            )
                        }
                        val onlinePart = if (onlineOnlyCount > 0) {
                            stringResource(Res.string.settings_online_only_count, onlineOnlyCount)
                        } else {
                            null
                        }
                        val summaryText = listOfNotNull(downloadPart, onlinePart).joinToString(", ")
                            .ifEmpty {
                                stringResource(
                                    Res.string.settings_translations_online_only,
                                    state.translations.size
                                )
                            }
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
                        contentDescription = stringResource(
                            Res.string.settings_remove_language,
                            state.language.selfName
                        ),
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
                        text = stringResource(Res.string.settings_translations_title),
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
                        stringResource(
                            Res.string.common_status_downloaded_with_size,
                            formatFileSize(translation.sizeBytes)
                        )
                    translation.isDownloaded -> stringResource(Res.string.common_status_downloaded)
                    isDownloading -> stringResource(Res.string.common_status_downloading_ellipsis)
                    translation.isDownloadable && translation.sizeBytes != null ->
                        stringResource(
                            Res.string.common_status_available_with_size,
                            formatFileSize(translation.sizeBytes)
                        )
                    translation.isDownloadable -> stringResource(Res.string.common_status_available)
                    else -> stringResource(Res.string.common_status_online_only)
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
                                contentDescription = stringResource(
                                    Res.string.settings_delete_translation,
                                    translation.targetLanguage.selfName
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    translation.isDownloadable -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(
                                    Res.string.settings_download_translation,
                                    translation.targetLanguage.selfName
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = stringResource(Res.string.common_status_online_only),
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
                        text = stringResource(
                            Res.string.settings_dictionary_size,
                            formatFileSize(dictionarySizeBytes)
                        ),
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
                            contentDescription = stringResource(
                                Res.string.settings_download_language,
                                language.selfName
                            ),
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
    val expandedStateDescription = stringResource(Res.string.common_state_expanded)
    val collapsedStateDescription = stringResource(Res.string.common_state_collapsed)
    val editTranslationsAction = stringResource(Res.string.settings_edit_translation_languages)
    val collapseAction = stringResource(Res.string.common_action_collapse)
    val expandAction = stringResource(Res.string.common_action_expand)
    val selectedState = stringResource(Res.string.common_state_selected)
    val notSelectedState = stringResource(Res.string.common_state_not_selected)

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
                // Collapsed: header + language summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            stateDescription = collapsedStateDescription
                        }
                        .clickable(
                            onClick = onToggleExpanded,
                            role = Role.Button,
                            onClickLabel = editTranslationsAction
                        )
                        .padding(AppSpacing.lg)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedLanguages.isEmpty()) {
                                stringResource(Res.string.settings_no_languages_selected)
                            } else {
                                stringResource(
                                    Res.string.settings_languages_selected,
                                    selectedLanguages.size,
                                    pluralEnding(selectedLanguages.size)
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = expandAction,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (selectedLanguages.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        val sorted = selectedLanguages.sortedBy { it.selfName }
                        val maxVisible = 3
                        val visible = sorted.take(maxVisible)
                        val remaining = sorted.size - maxVisible
                        visible.forEachIndexed { index, language ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = AppSpacing.xs),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                text = "${language.flag} ${language.selfName}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = AppSpacing.xs)
                            )
                        }
                        if (remaining > 0) {
                            Text(
                                text = stringResource(Res.string.settings_languages_more, remaining),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = AppSpacing.xs)
                            )
                        }
                    }
                }
            } else {
                // Expanded header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = expandedStateDescription
                        }
                        .clickable(
                            onClick = onToggleExpanded,
                            role = Role.Button,
                            onClickLabel = collapseAction
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
                                    stateDescription = if (isSelected) selectedState else notSelectedState
                                }
                                .clickable(
                                    onClick = { onToggleLanguage(language) },
                                    role = Role.Checkbox,
                                    onClickLabel = stringResource(
                                        Res.string.settings_toggle_translation_for_language,
                                        language.selfName
                                    )
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

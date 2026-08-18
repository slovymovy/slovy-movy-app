package com.slovy.slovymovyapp.ui.languagesetup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

internal enum class SectionVisualState {
    Active,
    Done,
    Locked
}

@Composable
internal fun LanguageSetupSection(
    number: Int,
    label: String,
    state: SectionVisualState,
    lockedHint: String?,
    content: @Composable () -> Unit
) {
    val active = state != SectionVisualState.Locked

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.xs, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (state == SectionVisualState.Done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.language_setup_step_completed, number),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.width(AppSpacing.sm))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (lockedHint != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = lockedHint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = MaterialTheme.uiItalic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }

        content()
    }
}

@Composable
internal fun LanguageSetupListCard(
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(content = content)
    }
}

@Composable
internal fun LanguageSetupRow(
    language: Language,
    selected: Boolean,
    enabled: Boolean,
    multiSelect: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (multiSelect) {
                        Modifier.toggleable(
                            value = selected,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onClick() }
                        )
                    } else {
                        Modifier.selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = onClick
                        )
                    }
                )
                .semantics(mergeDescendants = true) {}
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.flag,
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.width(AppSpacing.md))

                Text(
                    text = language.selfName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            SelectionIndicator(
                selected = selected,
                multiSelect = multiSelect,
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun LanguageSetupNoTranslationRow(
    selected: Boolean,
    enabled: Boolean,
    showDivider: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    LanguageSetupOptionRow(
        title = stringResource(Res.string.language_setup_no_translation_title),
        subtitle = stringResource(Res.string.language_setup_no_translation_subtitle),
        accessibilityDescription = stringResource(Res.string.language_setup_no_translation_accessibility),
        leadingIcon = Icons.Outlined.Public,
        selected = selected,
        enabled = enabled,
        showDivider = showDivider,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun LanguageSetupOptionRow(
    title: String,
    subtitle: String?,
    accessibilityDescription: String?,
    leadingIcon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    showDivider: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange
                )
                .semantics(mergeDescendants = true) {
                    if (accessibilityDescription != null) {
                        contentDescription = accessibilityDescription
                    }
                }
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(AppSpacing.md))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MaterialTheme.serifFontFamily,
                                fontStyle = MaterialTheme.uiItalic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SelectionIndicator(
                selected = selected,
                multiSelect = true,
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun LanguageRequestLink(onClick: () -> Unit) {
    val linkTag = "language_request"
    val beforeText = stringResource(Res.string.language_setup_request_language_before)
    val linkText = stringResource(Res.string.language_setup_request_language_link)
    val afterText = stringResource(Res.string.language_setup_request_language_after)
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentOnClick by rememberUpdatedState(onClick)
    val text = remember(beforeText, linkText, afterText, accentColor) {
        buildAnnotatedString {
            append(beforeText)
            append(" ")
            val link = LinkAnnotation.Clickable(
                tag = linkTag,
                linkInteractionListener = { currentOnClick() }
            )
            withLink(link) {
                withStyle(SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline)) {
                    append(linkText)
                }
            }
            append(afterText)
        }
    }

    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs),
        style = MaterialTheme.typography.bodyMedium.copy(
            color = textColor,
            fontFamily = MaterialTheme.serifFontFamily,
            fontStyle = MaterialTheme.uiItalic,
            textAlign = TextAlign.Center
        )
    )
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean
) {
    val shape = if (multiSelect) RoundedCornerShape(6.dp) else CircleShape
    val outlineColor = if (enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                color = if (selected && multiSelect) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
                shape = shape
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else outlineColor,
                shape = shape
            )
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected && multiSelect -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )

            selected -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}


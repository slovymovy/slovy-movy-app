package com.slovy.slovymovyapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Material 3 color scheme from Figma design
private val LightColorScheme = lightColorScheme(
    // Primary colors - Teal
    primary = androidx.compose.ui.graphics.Color(0xFF4A8B8B), // Teal
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE0EDED), // Light teal tint
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF1A3030), // Dark teal

    // Secondary colors - warm neutrals
    secondary = androidx.compose.ui.graphics.Color(0xFF6B635A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8E4D9),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF2D2D2D),

    // Tertiary colors - Sage Haze
    tertiary = androidx.compose.ui.graphics.Color(0xFF8B9482),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE8EBE6),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF2D2D2D),

    // Error colors - muted dusty rose
    error = androidx.compose.ui.graphics.Color(0xFFC17D7D),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF5E0E0),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF5A2E2E),

    // Surface colors
    background = androidx.compose.ui.graphics.Color(0xFFF9F7F2), // Warm Linen
    onBackground = androidx.compose.ui.graphics.Color(0xFF2D2D2D), // Charcoal Silk
    surface = androidx.compose.ui.graphics.Color(0xFFF9F7F2), // Warm Linen
    onSurface = androidx.compose.ui.graphics.Color(0xFF2D2D2D), // Charcoal Silk
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8EBE6), // Light sage tint
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF8B9482), // Sage Haze

    // Surface containers - Warm Linen → Morning Mist gradient
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF), // Morning Mist
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFFDFCFA),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFFBFAF7),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFF7F5F0),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFF3F1EC),

    // Outline colors - warm gray
    outline = androidx.compose.ui.graphics.Color(0xFFDDD8CC),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFE8E4D9),
)

// Material 3 dark color scheme from Figma design
private val DarkColorScheme = darkColorScheme(
    // Primary colors - Burnished Gold
    primary = androidx.compose.ui.graphics.Color(0xFFC5A367), // Burnished Gold
    onPrimary = androidx.compose.ui.graphics.Color(0xFF2C241C), // Dark espresso
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF3D342A), // Muted gold-brown
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFF5EBD7), // Light gold tint

    // Secondary colors - cool slate
    secondary = androidx.compose.ui.graphics.Color(0xFF9BA3B8),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF1E212E),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF2D3245),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE2E4ED),

    // Tertiary colors - deep sage
    tertiary = androidx.compose.ui.graphics.Color(0xFF8BA38E),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF1E212E),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF5C6B5E),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFC8D4C6),

    // Error colors - muted dusty red
    error = androidx.compose.ui.graphics.Color(0xFFD4908F),
    onError = androidx.compose.ui.graphics.Color(0xFF2A1414),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF4A2828),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFF2D0D0),

    // Surface colors
    background = androidx.compose.ui.graphics.Color(0xFF12141C), // Midnight Ink
    onBackground = androidx.compose.ui.graphics.Color(0xFFE2E4ED), // Soft Ghost
    surface = androidx.compose.ui.graphics.Color(0xFF12141C), // Midnight Ink
    onSurface = androidx.compose.ui.graphics.Color(0xFFE2E4ED), // Soft Ghost
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2A2D38), // Dark parchment tint
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9499B0), // Aged Parchment

    // Surface containers - Midnight Ink → Library Dust gradient
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0E1017),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF151720),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF191C26),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF1C1F2A),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF1E212E), // Library Dust

    // Outline colors - slate blue
    outline = androidx.compose.ui.graphics.Color(0xFF32384D),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF2A2D38),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

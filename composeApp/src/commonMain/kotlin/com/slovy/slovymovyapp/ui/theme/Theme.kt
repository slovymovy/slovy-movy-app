package com.slovy.slovymovyapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal indicating whether the current theme is dark.
 * Use this instead of isSystemInDarkTheme() to respect the theme set by AppTheme.
 */
val LocalIsDarkTheme = compositionLocalOf { false }

// Material 3 color scheme - Copper warm palette
private val LightColorScheme = lightColorScheme(
    // Primary colors - Copper
    primary = androidx.compose.ui.graphics.Color(0xFFB87333), // Copper
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFF5E6D8), // Light warm peach
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF3D2810), // Dark bronze

    // Secondary colors - Warm Taupe (brighter for text visibility)
    secondary = androidx.compose.ui.graphics.Color(0xFF8B7A68), // Brighter warm taupe
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFEDE6DD), // Light warm cream
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF2D261E), // Dark warm brown

    // Tertiary colors - Muted Teal (complementary)
    tertiary = androidx.compose.ui.graphics.Color(0xFF5A8080), // Muted teal
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE8E8), // Light teal tint
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF1A2D2D), // Dark teal

    // Error colors
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410E0B),

    // Surface colors
    background = androidx.compose.ui.graphics.Color(0xFFF9F7F2), // Warm Linen
    onBackground = androidx.compose.ui.graphics.Color(0xFF2D2620), // Warm dark brown
    surface = androidx.compose.ui.graphics.Color(0xFFF9F7F2), // Warm Linen
    onSurface = androidx.compose.ui.graphics.Color(0xFF2D2620), // Warm dark brown
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEBE4DB), // Warm light tan
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF5A524A), // Warm gray-brown

    // Surface containers - Warm Linen gradient
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFFCFAF6),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF7F4EE),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFF2EEE6),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFEDE8E0),

    // Outline colors - Warm gray
    outline = androidx.compose.ui.graphics.Color(0xFF9A9080), // Warm gray
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFCBC4B8), // Light warm gray
)

// Material 3 dark color scheme from Figma design
private val DarkColorScheme = darkColorScheme(
    // Primary colors - Burnished Gold
    primary = androidx.compose.ui.graphics.Color(0xFFC5A367), // Burnished Gold
    onPrimary = androidx.compose.ui.graphics.Color(0xFF2C241C), // Dark espresso
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF3D342A), // Muted gold-brown
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFF5EBD7), // Light gold tint

    // Secondary colors - cool slate (brighter for text visibility)
    secondary = androidx.compose.ui.graphics.Color(0xFFB0B8C8),
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

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

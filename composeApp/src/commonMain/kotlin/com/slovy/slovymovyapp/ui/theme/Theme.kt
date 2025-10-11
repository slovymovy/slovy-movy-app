package com.slovy.slovymovyapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Material 3 color scheme from Figma design
private val LightColorScheme = lightColorScheme(
    // Primary colors
    primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF21005D),

    // Secondary colors
    secondary = androidx.compose.ui.graphics.Color(0xFF625B71),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF1D192B),

    // Tertiary colors
    tertiary = androidx.compose.ui.graphics.Color(0xFF7D5260),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD8E4),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF31111D),

    // Error colors
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410E0B),

    // Surface colors
    background = androidx.compose.ui.graphics.Color(0xFFFEF7FF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1D1B20),
    surface = androidx.compose.ui.graphics.Color(0xFFFEF7FF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1D1B20),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE7E0EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF49454F),

    // Surface containers (from Figma)
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF7F2FA),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF3EDF7),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFECE6F0),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE6E0E9),

    // Outline colors
    outline = androidx.compose.ui.graphics.Color(0xFF79747E),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFCAC4D0),
)

// Material 3 dark color scheme from Figma design
private val DarkColorScheme = darkColorScheme(
    // Primary colors
    primary = androidx.compose.ui.graphics.Color(0xFFD0BCFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF381E72),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF4F378B),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),

    // Secondary colors
    secondary = androidx.compose.ui.graphics.Color(0xFFCCC2DC),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF332D41),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF4A4458),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),

    // Tertiary colors
    tertiary = androidx.compose.ui.graphics.Color(0xFFEFB8C8),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF492532),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF633B48),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD8E4),

    // Error colors
    error = androidx.compose.ui.graphics.Color(0xFFF2B8B5),
    onError = androidx.compose.ui.graphics.Color(0xFF601410),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF8C1D18),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC),

    // Surface colors
    background = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE6E1E5),
    surface = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE6E1E5),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF49454F),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFCAC4D0),

    // Surface containers (from Figma)
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0F0D13),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF1A1A1D),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF211F26),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF2B2930),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF36343B),

    // Outline colors
    outline = androidx.compose.ui.graphics.Color(0xFF938F99),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF49454F),
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
        content = content
    )
}

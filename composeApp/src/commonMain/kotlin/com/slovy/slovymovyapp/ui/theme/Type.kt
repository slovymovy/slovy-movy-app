package com.slovy.slovymovyapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import slovymovyapp.composeapp.generated.resources.DancingScript_VariableFont_wght
import slovymovyapp.composeapp.generated.resources.Res


@Composable
fun dancingFontFamily() = FontFamily(
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Thin),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Thin, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.ExtraLight),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.ExtraLight, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Light),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Light, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Normal),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Medium),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Medium, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.SemiBold),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.SemiBold, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Bold),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Bold, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.ExtraBold),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.ExtraBold, style = FontStyle.Italic),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Black),
    Font(Res.font.DancingScript_VariableFont_wght, weight = FontWeight.Black, style = FontStyle.Italic)
)

@Composable
fun appTypography() = Typography().run {

    val displayFontFamily = dancingFontFamily()

    copy(
        displayLarge = displayLarge.copy(fontFamily = displayFontFamily),
        displayMedium = displayMedium.copy(fontFamily = displayFontFamily),
        displaySmall = displaySmall.copy(fontFamily = displayFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = displayFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = displayFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = displayFontFamily),
        titleLarge = titleLarge.copy(fontFamily = displayFontFamily),
        titleMedium = titleMedium.copy(fontFamily = displayFontFamily),
        titleSmall = titleSmall.copy(fontFamily = displayFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = displayFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = displayFontFamily),
        bodySmall = bodySmall.copy(fontFamily = displayFontFamily),
        labelLarge = labelLarge.copy(fontFamily = displayFontFamily),
        labelMedium = labelMedium.copy(fontFamily = displayFontFamily),
        labelSmall = labelSmall.copy(fontFamily = displayFontFamily)
    )
}


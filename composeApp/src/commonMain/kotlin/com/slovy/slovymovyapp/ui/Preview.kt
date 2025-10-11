package com.slovy.slovymovyapp.ui

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider


class ThemePreviewProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(false, true)
}

@Composable
fun ThemedPreview(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    AppTheme(darkTheme = darkTheme) { content() }
}
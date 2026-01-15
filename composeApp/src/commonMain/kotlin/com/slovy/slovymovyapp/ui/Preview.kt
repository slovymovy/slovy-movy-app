package com.slovy.slovymovyapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.slovy.slovymovyapp.ui.theme.AppTheme


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
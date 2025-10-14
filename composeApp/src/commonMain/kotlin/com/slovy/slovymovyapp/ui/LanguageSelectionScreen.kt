package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.data.Language
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter


data class LanguageOption(
    val label: String,
    val language: Language
)

class LanguageSelectionViewModel(
    val title: String = "Choose your native language",
    val languages: List<LanguageOption> = Language.entries.map { LanguageOption(it.selfName, it) }
) : ViewModel()

@Composable
fun LanguageSelectionScreen(
    viewModel: LanguageSelectionViewModel,
    onLanguageChosen: (Language) -> Unit = { _ -> }
) {
    LanguageSelectionScreenContent(
        state = viewModel,
        onLanguageChosen = onLanguageChosen
    )
}

@Composable
fun LanguageSelectionScreenContent(
    state: LanguageSelectionViewModel,
    onLanguageChosen: (Language) -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium
            )

            state.languages.forEach { option ->
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageChosen(option.language) }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun LanguageSelectionScreenPreviewDefault(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSelectionScreenContent(
            state = LanguageSelectionViewModel(
                title = "Choose your native language",
                languages = Language.entries.map { LanguageOption(it.selfName, it) }
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSelectionScreenPreviewEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSelectionScreenContent(
            state = LanguageSelectionViewModel(
                title = "Choose your native language",
                languages = emptyList()
            )
        )
    }
}

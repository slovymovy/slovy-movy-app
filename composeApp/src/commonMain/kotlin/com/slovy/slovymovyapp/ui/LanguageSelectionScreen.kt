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
import org.jetbrains.compose.ui.tooling.preview.Preview

val languagesToCode = listOf(
    "English" to "en",
    "Русский" to "ru",
    "Nederlands" to "nl",
    "Polski" to "pl"
)

val codeToLanguage = languagesToCode.associate { it.second to it.first }

data class LanguageOption(
    val label: String,
    val code: String
)

class LanguageSelectionViewModel(
    val title: String = "Choose your native language",
    val languages: List<LanguageOption> = languagesToCode.map { (label, code) -> LanguageOption(label, code) }
) : ViewModel()

@Composable
fun LanguageSelectionScreen(
    viewModel: LanguageSelectionViewModel,
    onLanguageChosen: (String) -> Unit = { _ -> }
) {
    LanguageSelectionScreenContent(
        state = viewModel,
        onLanguageChosen = onLanguageChosen
    )
}

@Composable
fun LanguageSelectionScreenContent(
    state: LanguageSelectionViewModel,
    onLanguageChosen: (String) -> Unit = {}
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
                        .clickable { onLanguageChosen(option.code) }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun LanguageSelectionScreenPreviewDefault() {
    LanguageSelectionScreenContent(
        state = LanguageSelectionViewModel(
            title = "Choose your native language",
            languages = languagesToCode.map { (label, code) -> LanguageOption(label, code) }
        )
    )
}

@Preview
@Composable
private fun LanguageSelectionScreenPreviewEmpty() {
    LanguageSelectionScreenContent(
        state = LanguageSelectionViewModel(
            title = "Choose your native language",
            languages = emptyList()
        )
    )
}

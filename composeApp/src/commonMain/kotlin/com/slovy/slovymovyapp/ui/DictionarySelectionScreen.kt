package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

private val dictionaries = listOf(
    "English" to "en",
    "Русский" to "ru",
    "Nederlands" to "nl",
    "Polski" to "pl"
)

@Composable
fun DictionarySelectionScreen(
    onDictionaryChosen: (String) -> Unit = { _ -> }
) {
    val state = remember {
        DictionarySelectionUiState(
            title = "Choose dictionary",
            dictionaries = dictionaries.map { (label, code) -> DictionaryOption(label, code) }
        )
    }

    DictionarySelectionScreenContent(
        state = state,
        onDictionaryChosen = onDictionaryChosen
    )
}

@Composable
fun DictionarySelectionScreenContent(
    state: DictionarySelectionUiState,
    onDictionaryChosen: (String) -> Unit = {}
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

            state.dictionaries.forEach { option ->
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDictionaryChosen(option.code) }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

data class DictionarySelectionUiState(
    val title: String,
    val dictionaries: List<DictionaryOption>
)

data class DictionaryOption(
    val label: String,
    val code: String
)

@Preview
@Composable
private fun DictionarySelectionScreenPreviewDefault() {
    DictionarySelectionScreenContent(
        state = DictionarySelectionUiState(
            title = "Choose dictionary",
            dictionaries = dictionaries.map { (label, code) -> DictionaryOption(label, code) }
        )
    )
}

@Preview
@Composable
private fun DictionarySelectionScreenPreviewEmpty() {
    DictionarySelectionScreenContent(
        state = DictionarySelectionUiState(
            title = "Choose dictionary",
            dictionaries = emptyList()
        )
    )
}

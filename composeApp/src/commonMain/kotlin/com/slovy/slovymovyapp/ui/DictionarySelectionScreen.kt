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
import com.slovy.slovymovyapp.data.remote.dictionariesKnown
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

data class DictionaryOption(
    val label: String,
    val code: String
)

class DictionarySelectionViewModel(
    val title: String = "Choose dictionary",
    val dictionaries: List<DictionaryOption> = dictionariesKnown.map { (label, code) -> DictionaryOption(label, code) }
) : ViewModel() {
}

@Composable
fun DictionarySelectionScreen(
    viewModel: DictionarySelectionViewModel,
    onDictionaryChosen: (String) -> Unit = { _ -> }
) {
    DictionarySelectionScreenContent(
        state = viewModel,
        onDictionaryChosen = onDictionaryChosen
    )
}

@Composable
fun DictionarySelectionScreenContent(
    state: DictionarySelectionViewModel,
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

@Preview
@Composable
private fun DictionarySelectionScreenPreviewDefault(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DictionarySelectionScreenContent(
            state = DictionarySelectionViewModel(
                title = "Choose dictionary",
                dictionaries = dictionariesKnown.map { (label, code) -> DictionaryOption(label, code) }
            )
        )
    }
}

@Preview
@Composable
private fun DictionarySelectionScreenPreviewEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DictionarySelectionScreenContent(
            state = DictionarySelectionViewModel(
                title = "Choose dictionary",
                dictionaries = emptyList()
            )
        )
    }
}

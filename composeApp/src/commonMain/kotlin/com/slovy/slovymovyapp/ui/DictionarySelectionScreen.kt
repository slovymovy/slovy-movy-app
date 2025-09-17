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
import org.jetbrains.compose.ui.tooling.preview.Preview

private val dictionaries = listOf(
    "English" to "en",
    "Русский" to "ru",
    "Nederlands" to "nl",
    "Polski" to "pl"
)

@Preview
@Composable
fun DictionarySelectionScreen(
    onDictionaryChosen: (String) -> Unit = { _ -> }
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose dictionary",
                style = MaterialTheme.typography.headlineMedium
            )

            dictionaries.forEach { (label, code) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDictionaryChosen(code) }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

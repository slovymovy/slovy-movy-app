package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

private fun wordInfo(word: String, language: String?): String {
    // Simple hardcoded descriptions; could be localized
    val base = when (word.lowercase()) {
        "world" -> "The earth, together with all of its countries and peoples."
        "idea" -> "A thought or suggestion as to a possible course of action."
        "bass" -> "The lowest adult male singing voice or low-frequency sound."
        else -> "No info available."
    }
    val langLabel = when (language) {
        "ru" -> "[RU]"
        "nl" -> "[NL]"
        else -> "[EN]"
    }
    return "$langLabel $base"
}

@Preview
@Composable
fun WordDetailScreen(
    language: String? = null,
    word: String = "idea",
    onBack: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Word details",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = word,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = wordInfo(word, language),
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

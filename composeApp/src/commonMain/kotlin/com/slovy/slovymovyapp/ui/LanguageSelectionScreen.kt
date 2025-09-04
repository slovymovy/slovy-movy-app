package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlinx.serialization.json.JsonArray

@Composable
fun LanguageSelectionScreen(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit
) {
    val json = kotlinx.serialization.json.Json
    val available: List<String> = remember {
        val setting = settingsRepository.getById(Setting.Name.availible_languages)
        val arr = setting?.value
        if (arr is JsonArray) arr.map { it.toString().trim('"') } else listOf("English", "Russian", "Dutch")
    }
    var native by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var toLearn by androidx.compose.runtime.remember { mutableStateOf(setOf<String>()) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Select native language", style = MaterialTheme.typography.headlineSmall)
            available.forEach { lang ->
                val selected = native == lang
                Text(
                    text = (if (selected) "✓ " else "") + lang,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            native = lang
                            // Also ensure it's not in learn set
                            toLearn = toLearn - lang
                        }
                        .padding(vertical = 8.dp)
                )
            }

            Text(
                text = "Languages to learn",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
            available.forEach { lang ->
                val selectable = native == null || native != lang
                val selected = lang in toLearn
                Text(
                    text = (if (selected) "[x] " else "[ ] ") + lang + if (!selectable) " (native)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selectable) {
                            toLearn = if (selected) toLearn - lang else toLearn + lang
                        }
                        .padding(vertical = 8.dp)
                )
            }

            val canSave = native != null
            Text(
                text = if (canSave) "Save" else "Select native language to continue",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canSave) {
                        // Persist selections
                        native?.let {
                            settingsRepository.insert(
                                Setting(
                                    id = Setting.Name.native_language,
                                    value = json.parseToJsonElement("\"$it\"")
                                )
                            )
                        }
                        val arrJson = toLearn.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                        settingsRepository.insert(
                            Setting(
                                id = Setting.Name.languages_to_learn,
                                value = json.parseToJsonElement(arrJson)
                            )
                        )
                        onDone()
                    }
                    .padding(vertical = 16.dp)
            )
        }
    }
}

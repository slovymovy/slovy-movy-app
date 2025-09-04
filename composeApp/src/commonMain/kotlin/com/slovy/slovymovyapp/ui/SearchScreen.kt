package com.slovy.slovymovyapp.ui

// Using a simple text button instead of material icons to keep commonMain lightweight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.settings.SettingsRepository

private val dictionary = listOf("world", "idea", "bass")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    language: String?,
    settingsRepository: SettingsRepository,
    onWordSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { dictionary.filter { it.contains(query.trim(), ignoreCase = true) } }
    var showInfo by remember { mutableStateOf(false) }

    // Build language options: default "All languages" + languages_to_learn from settings
    val learnList: List<String> = remember {
        val setting = settingsRepository.getById(com.slovy.slovymovyapp.data.settings.Setting.Name.languages_to_learn)
        val arr = setting?.value
        if (arr is kotlinx.serialization.json.JsonArray) arr.map { it.toString().trim('"') } else emptyList()
    }
    val languageOptions = remember(learnList) { listOf("All languages") + learnList }
    var selectedFilter by remember { mutableStateOf("All languages") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Search") },
                    actions = {
                        TextButton(onClick = { showInfo = true }) {
                            Text("ℹ︎")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                // Language filter selector
                Text(
                    text = "Language: $selectedFilter", modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* noop - simple selector shows below */ }
                        .padding(bottom = 8.dp)
                )
                languageOptions.forEach { opt ->
                    val selected = opt == selectedFilter
                    Text(
                        text = (if (selected) "• " else "  ") + opt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFilter = opt }
                            .padding(vertical = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search a word") }
                )

                if (results.isEmpty() && query.isNotBlank()) {
                    Text(
                        text = "No results",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    results.forEach { word ->
                        Text(
                            text = word,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWordSelected(word) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                confirmButton = {
                    TextButton(onClick = { showInfo = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Selected language") },
                text = { Text(language ?: "Not selected") }
            )
        }
    }
}

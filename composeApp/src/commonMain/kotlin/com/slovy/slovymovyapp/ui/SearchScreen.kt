package com.slovy.slovymovyapp.ui

// Using a simple text button instead of material icons to keep commonMain lightweight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LanguageCard
import org.jetbrains.compose.ui.tooling.preview.Preview

private val fallbackDictionary = listOf("world", "idea", "bass")

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun SearchScreen(
    language: String? = null,
    dictionaryLanguage: String? = null,
    dataManager: DataDbManager? = null,
    onWordSelected: (LanguageCard, String) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }

    // Repository instance
    val repository = remember(dataManager) { dataManager?.let { DictionaryRepository(it) } }

    val results = remember(query, dictionaryLanguage, repository) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) emptyList()
        else {
            repository?.search(trimmed, dictionaryLanguage) ?: listOf()
        }
    }

    var showInfo by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
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
                    results.forEach { item ->
                        Text(
                            text = item.display,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val card = repository?.getLanguageCard(item.language, item.lemmaId)
                                    if (card != null) {
                                        onWordSelected(card, item.lemma)
                                    }
                                }
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

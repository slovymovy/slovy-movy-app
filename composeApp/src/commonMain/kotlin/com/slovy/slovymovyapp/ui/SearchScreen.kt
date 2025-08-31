package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
// Using a simple text button instead of material icons to keep commonMain lightweight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val dictionary = listOf("world", "idea", "bass")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    language: String?,
    onWordSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { dictionary.filter { it.contains(query.trim(), ignoreCase = true) } }
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
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
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

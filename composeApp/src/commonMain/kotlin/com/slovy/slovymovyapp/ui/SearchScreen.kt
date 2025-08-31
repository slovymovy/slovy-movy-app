package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val dictionary = listOf("world", "idea", "bass")

@Composable
fun SearchScreen(
    language: String?,
    onWordSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { dictionary.filter { it.contains(query.trim(), ignoreCase = true) } }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
}

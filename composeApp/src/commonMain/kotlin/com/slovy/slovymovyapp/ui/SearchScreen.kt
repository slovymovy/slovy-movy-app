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
import com.slovy.slovymovyapp.data.remote.DataDbManager
import org.jetbrains.compose.ui.tooling.preview.Preview

private val fallbackDictionary = listOf("world", "idea", "bass")

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun SearchScreen(
    language: String? = null,
    dictionaryLanguage: String? = null,
    dataManager: DataDbManager? = null,
    onWordSelected: (String) -> Unit = { _ -> }
) {
    var query by remember { mutableStateOf("") }

    data class SearchItem(val display: String, val lemma: String)

    // Open dictionary DB lazily when language is available
    val dictionaryDb = remember(dictionaryLanguage, dataManager) {
        if (dictionaryLanguage != null && dataManager != null) {
            try {
                dataManager.openDictionaryReadOnly(dictionaryLanguage)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    val results = remember(query, dictionaryDb) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) emptyList()
        else {
            val db = dictionaryDb
            if (db == null) {
                // Fallback to simple in-memory filtering for preview/no DB
                fallbackDictionary
                    .filter { it.contains(trimmed, ignoreCase = true) }
                    .map { SearchItem(display = "\"$it\"", lemma = it) }
                    .take(20)
            } else {
                val q = db.dictionaryQueries
                val items = mutableListOf<SearchItem>()
                val lemmasPresent = mutableSetOf<String>()

                fun addLemma(lemma: String) {
                    val display = "\"$lemma\""
                    if (items.none { it.display == display }) {
                        items.add(SearchItem(display = display, lemma = lemma))
                        // Track lemma presence (case-insensitive) to suppress its forms
                        lemmasPresent.add(lemma.lowercase())
                    }
                }

                fun addForm(lemma: String, form: String) {
                    // If lemma already present in results, do not include its forms
                    if (lemmasPresent.contains(lemma.lowercase())) return
                    val display = "\"$form\" form of \"$lemma\""
                    if (items.none { it.display == display }) {
                        items.add(SearchItem(display = display, lemma = lemma))
                    }
                }

                if (trimmed.length < 3) {
                    // Exact matches only for very short queries
                    val lemmaMatches: List<String> =
                        q.selectLemmasByWord(trimmed).executeAsList().map { it.lemma } +
                                q.selectLemmasByNormalized(trimmed).executeAsList().map { it.lemma }
                    lemmaMatches.forEach { addLemma(it) }

                    val formMatchesEquals: List<Pair<String, String>> =
                        q.selectLemmasByFormEquals(trimmed, 20).executeAsList().map { it.lemma to it.form } +
                                q.selectLemmasByFormNormalizedEquals(trimmed, 20).executeAsList()
                                    .map { it.lemma to it.form }
                    formMatchesEquals.forEach { (lemma, form) -> addForm(lemma, form) }
                } else {
                    // Prefix LIKE matches for queries with 3+ characters
                    val pattern = "$trimmed%"
                    val lemmaLike: List<String> =
                        q.selectLemmasLike(pattern, 20).executeAsList().map { it.lemma } +
                                q.selectLemmasNormalizedLike(pattern, 20).executeAsList().map { it.lemma }
                    lemmaLike.forEach { addLemma(it) }

                    val formLike: List<Pair<String, String>> =
                        q.selectLemmasFromFormsLike(pattern, 20).executeAsList().map { it.lemma to it.form } +
                                q.selectLemmasFromFormsNormalizedLike(pattern, 20).executeAsList()
                                    .map { it.lemma to it.form }
                    formLike.forEach { (lemma, form) -> addForm(lemma, form) }
                }

                // Limit total results to 20
                items.take(20)
            }
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
                    results.forEach { item ->
                        Text(
                            text = item.display,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWordSelected(item.lemma) }
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

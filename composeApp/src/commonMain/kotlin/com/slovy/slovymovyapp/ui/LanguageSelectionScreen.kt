package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlinx.serialization.json.*

@Composable
fun LanguageSelectionScreen(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit
) {
    val json = Json

    val availableAndFlags: Pair<List<String>, Map<String, String>> = remember {
        val setting = settingsRepository.getById(Setting.Name.availible_languages)
        val value = setting?.value
        if (value is JsonArray) {
            val names = mutableListOf<String>()
            val flags = mutableMapOf<String, String>()
            value.forEach { el: JsonElement ->
                val obj = el as? JsonObject
                val name = obj?.get("name")?.jsonPrimitive?.contentOrNull
                val flag = obj?.get("flag")?.jsonPrimitive?.contentOrNull ?: "🌐"
                if (!name.isNullOrBlank()) {
                    names += name
                    flags[name] = flag
                }
            }
            Pair(names.toList(), flags.toMap())
        } else Pair(listOf(), mapOf())
    }
    val available: List<String> = availableAndFlags.first
    val flagsByName: Map<String, String> = availableAndFlags.second
    var native by remember { mutableStateOf<String?>(null) }
    var toLearn by remember { mutableStateOf(setOf<String>()) }

    fun flagFor(lang: String): String = flagsByName[lang] ?: "🌐"

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            @Composable
            fun GradientCard(content: @Composable ColumnScope.() -> Unit) {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    // light gradient-like look using paddings (no draw brush in commonMain Material3)
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), content = content)
                }
            }

            Text(
                text = "I want to learn...",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            GradientCard {
                available.forEach { lang ->
                    val selected = lang in toLearn
                    val bgColor: Color =
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .clickable { toLearn = if (selected) toLearn - lang else toLearn + lang }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${flagFor(lang)}  $lang",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                text = "My native languages are...",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            GradientCard {
                available.forEach { lang ->
                    val selected = native == lang
                    val bgColor: Color =
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .clickable {
                                native = lang
                                // Do not mutate toLearn; allow same language to be both native and toLearn if user wants
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${flagFor(lang)}  $lang",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            val canSave = native != null
            androidx.compose.material3.Button(
                enabled = canSave,
                onClick = {
                    native?.let {
                        settingsRepository.insert(
                            Setting(id = Setting.Name.native_language, value = json.parseToJsonElement("\"$it\""))
                        )
                    }
                    val arrJson = toLearn.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    settingsRepository.insert(
                        Setting(id = Setting.Name.languages_to_learn, value = json.parseToJsonElement(arrJson))
                    )
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(top = 16.dp)
            ) {
                Text("Start Learning →")
            }
        }
    }
}

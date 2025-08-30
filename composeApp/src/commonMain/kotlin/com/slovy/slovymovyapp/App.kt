package com.slovy.slovymovyapp

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.ui.HomeScreen
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(settingsRepository: SettingsRepository) {
    // Minimal app from scratch: just show the Home screen
    HomeScreen(settingsRepository.getById(Setting.Name.WELCOME_MESSAGE)?.value?.jsonPrimitive?.content.toString())
}

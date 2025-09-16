package com.slovy.slovymovyapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.settings.SettingsRepository

fun main() = application {
    val db = DataDbManager(PlatformDbSupport(null)).openAppDatabase()
    val repo = SettingsRepository(db)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Open words"
    ) {
        val platform = PlatformDbSupport(null)
        App(repo, platform)
    }
}

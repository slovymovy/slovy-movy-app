package com.slovy.slovymovyapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.settings.SettingsRepository

fun main() = application {
    val driver = DriverFactory(null).createDriver("jdbc:sqlite:")
    val db = DatabaseProvider.createAppDatabase(driver)
    val repo = SettingsRepository(db)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Open words"
    ) {
        App(repo)
    }
}

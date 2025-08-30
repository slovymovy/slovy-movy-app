package com.slovy.slovymovyapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.settings.SettingsRepository

fun main() = application {
    val driver = DriverFactory(null).createDriver("jdbc:sqlite:")
    val db = DatabaseProvider.createDatabase(driver)
    val repo = SettingsRepository(db)

    Window(
        onCloseRequest = ::exitApplication,
        title = "slovymovyapp",
    ) {
        App(repo)
    }
}

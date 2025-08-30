package com.slovy.slovymovyapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.notes.NotesRepository

fun main() = application {
    val driver = DriverFactory(null).createDriver("desktop.db")
    val db = DatabaseProvider.createDatabase(driver)
    val repo = NotesRepository(db)

    Window(
        onCloseRequest = ::exitApplication,
        title = "slovymovyapp",
    ) {
        App(repository = repo)
    }
}

package com.slovy.slovymovyapp

import androidx.compose.ui.window.ComposeUIViewController
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.notes.NotesRepository

fun MainViewController() = ComposeUIViewController {
    val driver = DriverFactory(null).createDriver("ios.db")
    val db = DatabaseProvider.createDatabase(driver)
    val repo = NotesRepository(db)
    App(repository = repo)
}
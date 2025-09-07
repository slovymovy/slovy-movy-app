package com.slovy.slovymovyapp

import androidx.compose.ui.window.ComposeUIViewController
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.settings.SettingsRepository

fun MainViewController() = ComposeUIViewController {
    val driver = DriverFactory(null).createDriver("ios.db")
    val db = DatabaseProvider.createAppDatabase(driver)
    val repo = SettingsRepository(db)
    App(repo)
}
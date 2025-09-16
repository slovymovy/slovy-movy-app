package com.slovy.slovymovyapp

import androidx.compose.ui.window.ComposeUIViewController
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.settings.SettingsRepository

fun MainViewController() = ComposeUIViewController {
    val db = DataDbManager(PlatformDbSupport(null)).openAppDatabase()
    val repo = SettingsRepository(db)
    App(repo)
}
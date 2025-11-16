package com.slovy.slovymovyapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository

fun main() = application {

    Window(
        onCloseRequest = ::exitApplication,
        title = "Open words"
    ) {
        val platform = PlatformDbSupport()
        val db = DataDbManager.openAppDatabase(platform)
        val settingRepo = SettingsRepository(db)
        val dataDbManager = DataDbManager(platform, settingRepo, GoogleStorageBucketDataProvider())
        App(settingRepo, dataDbManager, platform)
    }
}

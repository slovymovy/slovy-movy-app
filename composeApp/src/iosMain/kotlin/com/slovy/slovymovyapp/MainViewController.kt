package com.slovy.slovymovyapp

import androidx.compose.ui.window.ComposeUIViewController
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository

fun MainViewController() = ComposeUIViewController {
    val platform = PlatformDbSupport()
    val db = DataDbManager.openAppDatabase(platform)
    val settingRepo = SettingsRepository(db)
    val dataDbManager = DataDbManager(platform, settingRepo, GoogleStorageBucketDataProvider())
    App(settingRepo, dataDbManager, platform)
}
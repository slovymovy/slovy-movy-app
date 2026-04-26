package com.slovy.slovymovyapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.generated.AppVersion

fun main() = application {
    val buildConfig = AppBuildConfig(
        versionName = AppVersion.NAME,
        versionCode = AppVersion.CODE,
        isDebug = false,
        applicationId = "com.slovy.slovymovyapp"
    )
    val windowTitle = if (buildConfig.isDebug) "OpenWords Debug" else "OpenWords"

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitle
    ) {
        val platform = PlatformDbSupport()
        val db = DataDbManager.openAppDatabase(platform)
        val settingRepo = SettingsRepository(db)
        val dataDbManager = DataDbManager(platform, settingRepo, GoogleStorageBucketDataProvider())
        App(settingRepo, dataDbManager, platform, buildConfig)
    }
}

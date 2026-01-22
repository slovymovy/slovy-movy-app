package com.slovy.slovymovyapp

import androidx.compose.ui.window.ComposeUIViewController
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import platform.Foundation.NSBundle

fun MainViewController() = ComposeUIViewController {
    val platform = PlatformDbSupport()
    val db = DataDbManager.openAppDatabase(platform)
    val settingRepo = SettingsRepository(db)
    val dataDbManager = DataDbManager(platform, settingRepo, GoogleStorageBucketDataProvider())
    val buildConfig = createBuildConfig()
    App(settingRepo, dataDbManager, platform, buildConfig)
}

private fun createBuildConfig(): AppBuildConfig {
    val bundle = NSBundle.mainBundle
    val infoDictionary = bundle.infoDictionary

    val versionName = infoDictionary?.get("CFBundleShortVersionString") as? String ?: "?"
    val versionCode = (infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 8 // version
    val bundleId = bundle.bundleIdentifier ?: ""
    val displayName = infoDictionary?.get("CFBundleDisplayName") as? String ?: "?"
    val isDebug = displayName.contains("debug", ignoreCase = true)

    return AppBuildConfig(
        versionName = versionName,
        versionCode = versionCode,
        isDebug = isDebug,
        applicationId = bundleId
    )
}
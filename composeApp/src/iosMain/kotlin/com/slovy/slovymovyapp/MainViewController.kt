package com.slovy.slovymovyapp

import androidx.compose.ui.window.ComposeUIViewController
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.generated.AppVersion
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

@Suppress("unused") // Called from the Swift shell through the Kotlin/Native framework.
fun MainViewController(): UIViewController {
    val platform = PlatformDbSupport()
    val buildConfig = createBuildConfig()
    return ComposeUIViewController {
        AppRoot(platform = platform, appBuildConfig = buildConfig, androidContext = null)
    }
}

private fun createBuildConfig(): AppBuildConfig {
    val bundle = NSBundle.mainBundle
    val infoDictionary = bundle.infoDictionary

    val bundleId = bundle.bundleIdentifier ?: ""
    val displayName = infoDictionary?.get("CFBundleDisplayName") as? String ?: "?"
    val isDebug = displayName.contains("debug", ignoreCase = true)

    return AppBuildConfig(
        versionName = AppVersion.NAME,
        versionCode = AppVersion.CODE,
        isDebug = isDebug,
        applicationId = bundleId
    )
}

package com.slovy.slovymovyapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.generated.AppVersion

fun main() = application {
    val buildConfig = AppBuildConfig(
        versionName = AppVersion.NAME,
        versionCode = AppVersion.CODE,
        isDebug = false,
        applicationId = "com.slovy.slovymovyapp"
    )
    val windowTitle = if (buildConfig.isDebug) "OpenWords Debug" else "OpenWords"
    val platform = PlatformDbSupport()

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitle
    ) {
        AppRoot(platform, buildConfig)
    }
}

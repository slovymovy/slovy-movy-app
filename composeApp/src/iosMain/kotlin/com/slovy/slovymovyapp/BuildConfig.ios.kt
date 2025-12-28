package com.slovy.slovymovyapp

import platform.Foundation.NSBundle

actual fun getAppBuildConfig(): AppBuildConfig {
    val bundle = NSBundle.mainBundle
    val infoDictionary = bundle.infoDictionary

    val versionName = infoDictionary?.get("CFBundleShortVersionString") as? String ?: "?"
    val versionCode = (infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 1
    val bundleId = bundle.bundleIdentifier ?: ""

    // Check if display name contains "debug" - we set this in Xcode for debug builds
    val displayName = infoDictionary?.get("CFBundleDisplayName") as? String ?: "?"
    val isDebug = displayName.lowercase().contains("debug", ignoreCase = true)

    return AppBuildConfig(
        versionName = versionName,
        versionCode = versionCode,
        isDebug = isDebug,
        applicationId = bundleId
    )
}

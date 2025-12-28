package com.slovy.slovymovyapp

actual fun getAppBuildConfig(): AppBuildConfig = AppBuildConfig(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    isDebug = BuildConfig.DEBUG,
    applicationId = BuildConfig.APPLICATION_ID
)

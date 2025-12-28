package com.slovy.slovymovyapp

actual fun getAppBuildConfig(): AppBuildConfig = AppBuildConfig(
    versionName = "1.0.0",
    versionCode = 1,
    isDebug = false,
    applicationId = "com.slovy.slovymovyapp"
)

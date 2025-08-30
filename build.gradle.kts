plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Ensure sqlite tmp folder exists on Windows to avoid sqlite-jdbc extracting into C:\\WINDOWS
// See: https://github.com/sqldelight/sqldelight/issues/5312
if (System.getProperty("os.name").lowercase().contains("windows")) {
    val tmp = file(".gradle/sqlite-tmp")
    if (!tmp.exists()) tmp.mkdirs()
}
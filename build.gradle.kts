plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.valkyrie) apply false
    alias(libs.plugins.googleServices) apply false
}

val gitCommitCount = providers.of(GitCommitCountValueSource::class) {}.get()
val appVersionBase = libs.versions.appVersionBase.get()
extra["versionCode"] = gitCommitCount
extra["versionName"] = "$appVersionBase.$gitCommitCount"

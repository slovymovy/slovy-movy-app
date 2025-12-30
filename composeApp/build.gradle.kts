import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.serialization)
    alias(libs.plugins.sqldelight)
}

val gitBranchProvider: Provider<String> = providers.of(GitBranchValueSource::class.java) {}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    androidLibrary {
        namespace = "com.slovy.slovymovyapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTestBuilder {}

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            instrumentationRunnerArguments[TestEnvironment.GITHUB_TOKEN] = System.getenv(TestEnvironment.GITHUB_TOKEN) ?: ""
            instrumentationRunnerArguments[TestEnvironment.IS_TEST] = "true"
            instrumentationRunnerArguments[TestEnvironment.GIT_BRANCH] =  gitBranchProvider.get()
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(compose.uiTooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
            implementation(projects.shared)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.ktor.client.cio)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.espresso.core)
            implementation(libs.junit)
            implementation(libs.sqldelight.androidDriver)
        }
    }
}

sqldelight {
    linkSqlite = true
}

compose.desktop {
    application {
        mainClass = "com.slovy.slovymovyapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.slovy.slovymovyapp"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<Test> {
    environment(TestEnvironment.GITHUB_TOKEN, System.getenv(TestEnvironment.GITHUB_TOKEN) ?: "")
    environment(TestEnvironment.IS_TEST, "true")
    environment(TestEnvironment.GIT_BRANCH, provider { gitBranchProvider.get() }.get())
}

tasks.withType<KotlinNativeTest> {
    environment(TestEnvironment.GITHUB_TOKEN, System.getenv(TestEnvironment.GITHUB_TOKEN) ?: "")
    environment(TestEnvironment.IS_TEST, "true")
    environment(TestEnvironment.GIT_BRANCH, provider { gitBranchProvider.get() }.get())
    // iOS simulator needs SIMCTL_CHILD_ prefix to propagate environment variables
    val prefix = "SIMCTL_CHILD_"
    environment("$prefix${TestEnvironment.GITHUB_TOKEN}", System.getenv(TestEnvironment.GITHUB_TOKEN) ?: "")
    environment("$prefix${TestEnvironment.IS_TEST}", "true")
    environment("$prefix${TestEnvironment.GIT_BRANCH}", provider { gitBranchProvider.get() }.get())
}


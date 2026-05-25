import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.valkyrie)
}

val testServerPort = 9090

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
        enableCoreLibraryDesugaring = true

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTestBuilder {}

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
            androidResources {
                enable = true
            }
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            instrumentationRunnerArguments[TestEnvironment.IS_TEST] = "true"
            instrumentationRunnerArguments[TestEnvironment.TEST_SERVER_PORT] = testServerPort.toString()
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.slovy.openwords")
            export(projects.shared)
        }
    }

    jvm("desktop")

    sourceSets {
        val sharedAndroidTestDir = "src/androidTest/kotlin"
        val desktopMain by getting
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.ui.tooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.emoji2)

            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.customview.poolingcontainer)

            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.perf)
            implementation(libs.firebase.appcheck.playintegrity)
            implementation(libs.firebase.appcheck.debug)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
            api(projects.shared)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.ktor.client.cio)
        }
        getByName("androidHostTest") {
            kotlin.srcDir(sharedAndroidTestDir)
            dependencies {
                implementation(libs.androidx.test.core)
                implementation(libs.robolectric)
            }
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir(sharedAndroidTestDir)
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.testExt.junit)
                implementation(libs.androidx.espresso.core)
                implementation(libs.junit)
                implementation(libs.sqldelight.androidDriver)
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugarJdkLibs)
}

sqldelight {
    linkSqlite = true
}

valkyrie {
    packageName = "com.slovy.slovymovyapp.ui.icons"

    iconPack {
        name = "SlovyIcons"
        targetSourceSet = "commonMain"
    }

    imageVector {
        generatePreview = true
    }
}

val generateAppVersion = tasks.register<WriteAppVersionTask>("generateAppVersion") {
    versionName.set(rootProject.extra["versionName"] as String)
    versionCode.set(rootProject.extra["versionCode"] as Int)
    outputDir.set(layout.buildDirectory.dir("generated/appversion/commonMain/kotlin"))
}

val generateIosVersionXcconfig = tasks.register<WriteIosVersionXcconfigTask>("generateIosVersionXcconfig") {
    versionName.set(rootProject.extra["versionName"] as String)
    versionCode.set(rootProject.extra["versionCode"] as Int)
    outputFile.set(rootProject.layout.projectDirectory.file("iosApp/Configuration/Version.xcconfig"))
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateAppVersion.map { it.outputDir })
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn("generateValkyrieImageVector")
    dependsOn(generateAppVersion)
    dependsOn(generateIosVersionXcconfig)
}

compose.desktop {
    application {
        mainClass = "com.slovy.slovymovyapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.slovy.slovymovyapp"
            packageVersion = rootProject.extra["versionName"] as String
        }
    }
}

tasks.withType<Test> {
    environment(TestEnvironment.IS_TEST, "true")
    environment(TestEnvironment.TEST_SERVER_PORT, testServerPort.toString())
}

tasks.withType<KotlinNativeTest> {
    environment(TestEnvironment.IS_TEST, "true")
    environment(TestEnvironment.TEST_SERVER_PORT, testServerPort.toString())
    // iOS simulator needs SIMCTL_CHILD_ prefix to propagate environment variables
    val prefix = "SIMCTL_CHILD_"
    environment("$prefix${TestEnvironment.IS_TEST}", "true")
    environment("$prefix${TestEnvironment.TEST_SERVER_PORT}", testServerPort.toString())
}


val testServer = registerTestServer(testServerPort)

tasks.withType<Test>().configureEach {
    usesTestServer(testServer)
}

tasks.withType<KotlinNativeTest>().configureEach {
    usesTestServer(testServer)
}

configureTasksToUseTestServer(testServer, "connectedAndroidTest", "connectedAndroidDeviceTest")

tasks.register<VerifyLocalizationKeysTask>("verifyLocalizationKeys") {
    group = "verification"
    description = "Ensures every localized compose resource file has the same keys as base values."
    resourcesDir.set(layout.projectDirectory.dir("src/commonMain/composeResources"))
}

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

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTestBuilder {}

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
            @Suppress("UnstableApiUsage")
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

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn("generateValkyrieImageVector")
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


val testServerService: Provider<TestServerService> = gradle.sharedServices.registerIfAbsent(
    "testServerService",
    TestServerService::class.java
) {
    val serverProject = project(":server")
    parameters.classpath.set(
        serverProject.provider {
            val config = serverProject.configurations.getByName("runtimeClasspath")
            val classesDir = serverProject.layout.buildDirectory.dir("classes/kotlin/main").get().asFile.absolutePath
            val resourcesDir = serverProject.layout.buildDirectory.dir("resources/main").get().asFile.absolutePath
            (listOf(classesDir, resourcesDir) + config.files.map { it.absolutePath }).distinct()
        }
    )
    parameters.workingDir.set(rootProject.projectDir.absolutePath)
    parameters.port.set(testServerPort)
    parameters.dbDir.set(File(rootProject.projectDir, ".test-db-files").absolutePath)
    maxParallelUsages.set(1)
}

val startTestServer = tasks.register<StartTestServerTask>("startTestServer") {
    dependsOn(":server:classes")
}

tasks.withType<Test>().configureEach {
    dependsOn(":server:classes")
    dependsOn(startTestServer)
    usesService(testServerService)
}

tasks.withType<KotlinNativeTest>().configureEach {
    dependsOn(":server:classes")
    dependsOn(startTestServer)
    usesService(testServerService)
}

tasks.matching { it.name == "connectedAndroidTest" || it.name == "connectedAndroidDeviceTest" }.configureEach {
    dependsOn(":server:classes")
    dependsOn(startTestServer)
    usesService(testServerService)
}

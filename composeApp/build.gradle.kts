import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.serialization)
    alias(libs.plugins.sqldelight)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        instrumentedTestVariant {
            sourceSetTree.set(KotlinSourceSetTree.test)
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
        androidUnitTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }
    }
}

val githubTokenEnvName = "ACCESS_TO_GH_TOKEN"
val isTest = "IS_TEST"
val gitBranchEnvName = "GIT_BRANCH_REF"

abstract class GitBranchValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val branchNameFromEnv = System.getenv("BRANCH_NAME")
        if (branchNameFromEnv != null) {
            return branchNameFromEnv
        }

        val branchOutput = ByteArrayOutputStream()
        execOperations.exec {
            commandLine = listOf("git", "rev-parse", "--abbrev-ref", "HEAD")
            standardOutput = branchOutput
        }
        val branchName = branchOutput.toString().trim()

        // Check if branch exists on remote
        val remoteCheckResult = execOperations.exec {
            commandLine = listOf("git", "rev-parse", "--verify", "refs/remotes/origin/$branchName")
            isIgnoreExitValue = true
        }

        return if (remoteCheckResult.exitValue == 0) branchName else "main"
    }
}

val gitBranchProvider = providers.of(GitBranchValueSource::class.java) {}

android {
    namespace = "com.slovy.slovymovyapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.slovy.slovymovyapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "Alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments[githubTokenEnvName] =
            System.getenv(githubTokenEnvName) ?: ""
        testInstrumentationRunnerArguments[isTest] = "true"
        testInstrumentationRunnerArguments[gitBranchEnvName] = provider { gitBranchProvider.get() }.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    sourceSets {
        getByName("debug") {
            res.srcDirs("src/androidDebug/res")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.sqldelight.androidDriver)
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
    environment(githubTokenEnvName, System.getenv(githubTokenEnvName) ?: "")
    environment(isTest, "true")
    environment(gitBranchEnvName, provider { gitBranchProvider.get() }.get())
}

tasks.withType<KotlinNativeTest> {
    environment(githubTokenEnvName, System.getenv(githubTokenEnvName) ?: "")
    environment(isTest, "true")
    environment(gitBranchEnvName, provider { gitBranchProvider.get() }.get())
    // iOS simulator needs SIMCTL_CHILD_ prefix to propagate environment variables
    val prefix = "SIMCTL_CHILD_"
    environment("$prefix$githubTokenEnvName", System.getenv(githubTokenEnvName) ?: "")
    environment("$prefix$isTest", "true")
    environment("$prefix$gitBranchEnvName", provider { gitBranchProvider.get() }.get())
}

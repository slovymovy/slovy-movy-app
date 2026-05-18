import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.firebasePerf)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

val minifiedR8Rules = layout.projectDirectory.file("proguard-rules.pro")
val minifiedTestR8Rules = layout.projectDirectory.file("proguard-minified-test.pro")
val testServerPort = 9090

android {
    namespace = "com.slovy.slovymovyapp.androidApp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    // Run androidTest against a release-like, minified build type so instrumentation
    // tests exercise the same R8/resource shrinking path as production.
    testBuildType = "minifiedTest"

    defaultConfig {
        applicationId = "com.slovy.slovymovyapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        testInstrumentationRunnerArguments[TestEnvironment.IS_TEST] = "true"
        testInstrumentationRunnerArguments[TestEnvironment.TEST_SERVER_PORT] = testServerPort.toString()
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                minifiedR8Rules.asFile
            )
        }
        create("minifiedTest") {
            // Mirrors release shrinking/optimization, but uses debug signing and the
            // debug application ID so connected tests can install it safely in CI.
            initWith(getByName("release"))
            matchingFallbacks += listOf("release", "debug")
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-minified-test"
            proguardFiles(minifiedTestR8Rules.asFile)
            testProguardFiles(minifiedTestR8Rules.asFile)
        }
    }

    sourceSets {
        getByName("debug") {
            res.srcDirs("src/debug/res")
        }
        getByName("minifiedTest") {
            res.srcDirs("src/debug/res")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.register("checkReleaseOptimizedBuild") {
    group = "verification"
    description = "Runs release unit tests and assembles the minified, resource-shrunk release APK."

    dependsOn("testMinifiedTestUnitTest", "assembleRelease")
}

tasks.register("connectedMinifiedAndroidTest") {
    group = "verification"
    description = "Runs Android instrumentation tests against a signed, minified, resource-shrunk build."

    dependsOn("connectedMinifiedTestAndroidTest")
}

val testServer = registerTestServer(testServerPort)
configureTasksToUseTestServer(
    testServer,
    "connectedAndroidTest",
    "connectedMinifiedAndroidTest",
    "connectedMinifiedTestAndroidTest"
)

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    coreLibraryDesugaring(libs.android.desugarJdkLibs)

    debugImplementation(libs.androidx.lifecycle.viewmodel.savedstate)
    debugImplementation(libs.androidx.lifecycle.runtime)
    debugImplementation(libs.androidx.customview.poolingcontainer)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutinesCore)
    androidTestImplementation(libs.kotlinx.ioCore)
    androidTestImplementation(libs.junit)
}

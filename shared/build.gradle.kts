import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.serialization)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        instrumentedTestVariant {
            sourceSetTree.set(KotlinSourceSetTree.test)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.serializationJson)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val appDatabaseName = "AppDatabase"
val dictionaryDatabaseName = "DictionaryDatabase"
val translationDatabaseName = "TranslationDatabase"
sqldelight {
    linkSqlite = true
    databases {
        create(appDatabaseName) {
            packageName.set("com.slovy.slovymovyapp.db")
            deriveSchemaFromMigrations.set(false)
            // https://github.com/sqldelight/sqldelight/issues/5312
            verifyMigrations.set(!OperatingSystem.current().isWindows)
            // https://github.com/sqldelight/sqldelight/issues/5312
            verifyDefinitions.set(!OperatingSystem.current().isWindows)
            srcDirs.setFrom("src/commonMain/sqldelight/appdb")
        }
        create(dictionaryDatabaseName) {
            packageName.set("com.slovy.slovymovyapp.dictionary")
            deriveSchemaFromMigrations.set(false)
            verifyMigrations.set(false)
            verifyDefinitions.set(false)
            srcDirs.setFrom("src/commonMain/sqldelight/dictionarydb")
        }
        create(translationDatabaseName) {
            packageName.set("com.slovy.slovymovyapp.translation")
            deriveSchemaFromMigrations.set(false)
            verifyMigrations.set(false)
            verifyDefinitions.set(false)
            srcDirs.setFrom("src/commonMain/sqldelight/translationdb")
        }
    }
}

android {
    namespace = "com.slovy.slovymovyapp.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// Disable SqlDelight verification tasks on Windows due to https://github.com/sqldelight/sqldelight/issues/5312
if (OperatingSystem.current().isWindows) {
    tasks.matching { task ->
        task.name.startsWith("verify") && listOf(
            appDatabaseName,
            dictionaryDatabaseName,
            translationDatabaseName
        ).any { task.name.contains(it) }
    }.configureEach {
        enabled = false
    }
}

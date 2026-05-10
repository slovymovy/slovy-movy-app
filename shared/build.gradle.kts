import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val excludeMobile = project.findProperty("excludeMobile")?.toString()?.toBoolean() ?: false

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.serialization)
}

group = "com.slovy.slovymovyapp"
version = rootProject.extra["versionName"] as String

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    androidLibrary {
        namespace = "com.slovy.slovymovyapp.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTestBuilder {}

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
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
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/appdb"))
        }
        create(dictionaryDatabaseName) {
            packageName.set("com.slovy.slovymovyapp.dictionary")
            deriveSchemaFromMigrations.set(false)
            // https://github.com/sqldelight/sqldelight/issues/5312
            verifyMigrations.set(!OperatingSystem.current().isWindows)
            // https://github.com/sqldelight/sqldelight/issues/5312
            verifyDefinitions.set(!OperatingSystem.current().isWindows)
            srcDirs.setFrom("src/commonMain/sqldelight/dictionarydb")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/dictionarydb"))
        }
        create(translationDatabaseName) {
            packageName.set("com.slovy.slovymovyapp.translation")
            deriveSchemaFromMigrations.set(false)
            // https://github.com/sqldelight/sqldelight/issues/5312
            verifyMigrations.set(!OperatingSystem.current().isWindows)
            // https://github.com/sqldelight/sqldelight/issues/5312
            verifyDefinitions.set(!OperatingSystem.current().isWindows)
            srcDirs.setFrom("src/commonMain/sqldelight/translationdb")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/translationdb"))
        }
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

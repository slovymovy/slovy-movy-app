plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
}

group = "com.slovy.slovymovyapp"
version = "1.0.0"
application {
    mainClass.set("com.slovy.slovymovyapp.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    api(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.sqldelight.sqliteDriver)
    implementation(libs.commons.text)

    // AI providers
    api(libs.google.genai) {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    api(libs.openai.java)

    // GitHub client
    api(libs.github.api)

    // Google Cloud
    implementation(libs.google.cloud.tasks)
    implementation(libs.google.cloud.core)
    implementation(libs.google.auth.library)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.test)
}

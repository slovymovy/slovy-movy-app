plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
}

group = "com.slovy.slovymovyapp"
version = rootProject.extra["versionName"] as String
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
    implementation(libs.kotlinx.datetime)
    implementation(libs.sqldelight.sqliteDriver)
    implementation(libs.commons.text)
    implementation(libs.google.cloud.logging.logback)

    // AI providers
    api(libs.google.genai) {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    api(libs.openai.java)
    api(libs.jtokkit)

    // GitHub client
    api(libs.github.api)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // Google Cloud
    implementation(libs.google.cloud.tasks)
    implementation(libs.google.cloud.core)
    implementation(libs.google.auth.library)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.test)
}

tasks.register<JavaExec>("runDbPrepTool") {
    group = "application"
    description = "Run DbPrepTool CLI to build/rebuild SQLite DB files from db-extract + processed JSON"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.slovy.slovymovyapp.builder.DbPrepToolKt")
}

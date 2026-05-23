package com.slovy.slovymovyapp.logging

enum class AppLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

interface AppLogSink {
    fun log(level: AppLogLevel, tag: String, message: String, throwable: Throwable?)
}

object NoOpAppLogSink : AppLogSink {
    override fun log(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

data class AppLogEntry(
    val createdAtEpochMs: Long,
    val level: AppLogLevel,
    val tag: String,
    val message: String,
    val throwableLabel: String?,
)

expect object AppLogger {
    var remoteLogger: AppLogSink
    var developerLogger: AppLogSink

    fun recentDeveloperLogs(): List<AppLogEntry>

    fun debug(tag: String, message: String, throwable: Throwable?)
    fun info(tag: String, message: String, throwable: Throwable?)
    fun warn(tag: String, message: String, throwable: Throwable?)
    fun error(tag: String, message: String, throwable: Throwable?)
}

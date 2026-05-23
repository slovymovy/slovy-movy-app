package com.slovy.slovymovyapp.logging

import platform.Foundation.NSLog
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

actual object AppLogger {
    actual var remoteLogger: AppLogSink = NoOpAppLogSink
    actual var developerLogger: AppLogSink = NoOpAppLogSink

    private val developerLogBuffer = DeveloperLogBuffer()

    actual fun recentDeveloperLogs(): List<AppLogEntry> = developerLogBuffer.snapshot()

    actual fun clearDeveloperLogs() {
        developerLogBuffer.clear()
    }

    actual fun debug(tag: String, message: String, throwable: Throwable?) {
        log("DEBUG", tag, message, throwable)
        logDeveloper(AppLogLevel.DEBUG, tag, message, throwable)
    }

    actual fun info(tag: String, message: String, throwable: Throwable?) {
        log("INFO", tag, message, throwable)
        logDeveloper(AppLogLevel.INFO, tag, message, throwable)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        log("WARN", tag, message, throwable)
        logDeveloper(AppLogLevel.WARN, tag, message, throwable)
        logRemote(AppLogLevel.WARN, tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        log("ERROR", tag, message, throwable)
        logDeveloper(AppLogLevel.ERROR, tag, message, throwable)
        logRemote(AppLogLevel.ERROR, tag, message, throwable)
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val cause = throwable?.let { " ($it)" }.orEmpty()
        NSLog("$level/$tag: $message$cause")
    }

    private fun logDeveloper(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        appendDeveloperLog(level, tag, message, throwable)
        runCatching {
            developerLogger.log(level, tag, message, throwable)
        }.onFailure {
            NSLog("WARN/$tag: Developer logging failed ($it)")
        }
    }

    private fun logRemote(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            remoteLogger.log(level, tag, message, throwable)
        }.onFailure {
            NSLog("WARN/$tag: Remote logging failed ($it)")
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun appendDeveloperLog(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        developerLogBuffer.append(
            AppLogEntry(
                createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                level = level,
                tag = tag,
                message = message,
                throwableLabel = throwable?.toLogLabel(),
            ),
        )
    }

    private fun Throwable.toLogLabel(): String =
        "${this::class.simpleName ?: "Error"}: ${message ?: "no message"}"

}

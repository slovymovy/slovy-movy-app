package com.slovy.slovymovyapp.logging

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

actual object AppLogger {
    actual var remoteLogger: AppLogSink = NoOpAppLogSink
    actual var developerLogger: AppLogSink = NoOpAppLogSink

    private val developerLogBuffer = DeveloperLogBuffer()

    @Synchronized
    actual fun recentDeveloperLogs(): List<AppLogEntry> = developerLogBuffer.snapshot()

    @Synchronized
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

    @OptIn(ExperimentalTime::class)
    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val line = "${Clock.System.now()} $level/$tag: $message"
        if (level == "WARN" || level == "ERROR") {
            System.err.println(line)
            throwable?.printStackTrace(System.err)
        } else {
            System.out.println(line)
            throwable?.printStackTrace()
        }
    }

    @OptIn(ExperimentalTime::class)
    @Synchronized
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

    private fun logDeveloper(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        appendDeveloperLog(level, tag, message, throwable)
        runCatching {
            developerLogger.log(level, tag, message, throwable)
        }.onFailure {
            System.err.println("WARN/$tag: Developer logging failed")
            it.printStackTrace(System.err)
        }
    }

    private fun logRemote(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            remoteLogger.log(level, tag, message, throwable)
        }.onFailure {
            System.err.println("WARN/$tag: Remote logging failed")
            it.printStackTrace(System.err)
        }
    }

    private fun Throwable.toLogLabel(): String =
        "${this::class.simpleName ?: "Error"}: ${message ?: "no message"}"

}

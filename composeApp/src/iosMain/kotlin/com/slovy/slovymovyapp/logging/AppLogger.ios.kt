package com.slovy.slovymovyapp.logging

import platform.Foundation.NSLog
import platform.Foundation.NSRecursiveLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

actual object AppLogger {
    actual var remoteLogger: AppLogSink = NoOpAppLogSink
    actual var developerLogger: AppLogSink = NoOpAppLogSink

    private val developerLogBuffer = DeveloperLogBuffer()
    private val developerLogLock = NSRecursiveLock()
    private var debugLoggingEnabledValue = false

    actual var debugLoggingEnabled: Boolean
        get() = withDeveloperLogLock { debugLoggingEnabledValue }
        set(value) {
            withDeveloperLogLock {
                debugLoggingEnabledValue = value
            }
        }

    actual fun recentDeveloperLogs(): List<AppLogEntry> =
        withDeveloperLogLock {
            developerLogBuffer.snapshot()
        }

    actual fun clearDeveloperLogs() {
        withDeveloperLogLock {
            developerLogBuffer.clear()
        }
    }

    actual fun debug(tag: String, throwable: Throwable?, message: () -> String) {
        if (!debugLoggingEnabled) return
        val resolvedMessage = message()
        log("DEBUG", tag, resolvedMessage, throwable)
        logDeveloper(AppLogLevel.DEBUG, tag, resolvedMessage, throwable)
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
        val entry = AppLogEntry(
            createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            level = level,
            tag = tag,
            message = message,
            throwableLabel = throwable?.toLogLabel(),
        )
        withDeveloperLogLock {
            developerLogBuffer.append(entry)
        }
    }

    private fun Throwable.toLogLabel(): String =
        "${this::class.simpleName ?: "Error"}: ${message ?: "no message"}"

    private inline fun <T> withDeveloperLogLock(block: () -> T): T {
        developerLogLock.lock()
        return try {
            block()
        } finally {
            developerLogLock.unlock()
        }
    }

}

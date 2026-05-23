package com.slovy.slovymovyapp.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

actual object AppLogger {
    actual var remoteLogger: AppLogSink = NoOpAppLogSink
    actual var developerLogger: AppLogSink = NoOpAppLogSink
    @Volatile
    actual var debugLoggingEnabled: Boolean = false

    private val developerLogBuffer = DeveloperLogBuffer()

    @Synchronized
    actual fun recentDeveloperLogs(): List<AppLogEntry> = developerLogBuffer.snapshot()

    @Synchronized
    actual fun clearDeveloperLogs() {
        developerLogBuffer.clear()
    }

    actual fun debug(tag: String, throwable: Throwable?, message: () -> String) {
        if (!debugLoggingEnabled) return
        val resolvedMessage = message()
        if (throwable == null) Log.d(tag, resolvedMessage) else Log.d(tag, resolvedMessage, throwable)
        logDeveloper(AppLogLevel.DEBUG, tag, resolvedMessage, throwable)
    }

    actual fun info(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.i(tag, message) else Log.i(tag, message, throwable)
        logDeveloper(AppLogLevel.INFO, tag, message, throwable)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
        logDeveloper(AppLogLevel.WARN, tag, message, throwable)
        logRemote(AppLogLevel.WARN, tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
        logDeveloper(AppLogLevel.ERROR, tag, message, throwable)
        logRemote(AppLogLevel.ERROR, tag, message, throwable)
    }

    @Synchronized
    private fun appendDeveloperLog(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        developerLogBuffer.append(
            AppLogEntry(
                createdAtEpochMs = System.currentTimeMillis(),
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
            Log.w(tag, "Developer logging failed", it)
        }
    }

    private fun logRemote(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            remoteLogger.log(level, tag, message, throwable)
        }.onFailure {
            Log.w(tag, "Remote logging failed", it)
        }
    }

    private fun Throwable.toLogLabel(): String =
        "${this::class.simpleName ?: "Error"}: ${message ?: "no message"}"

}

class FirebaseCrashlyticsAppLogSink : AppLogSink {
    override fun log(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log("${level.name}/$tag: $message")
        if (throwable != null) {
            crashlytics.recordException(throwable)
        }
    }
}

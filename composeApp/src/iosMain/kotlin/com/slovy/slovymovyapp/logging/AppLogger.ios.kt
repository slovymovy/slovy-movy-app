package com.slovy.slovymovyapp.logging

import platform.Foundation.NSLog

actual object AppLogger {
    actual var remoteLogger: AppLogSink = NoOpAppLogSink

    actual fun debug(tag: String, message: String, throwable: Throwable?) {
        log("DEBUG", tag, message, throwable)
    }

    actual fun info(tag: String, message: String, throwable: Throwable?) {
        log("INFO", tag, message, throwable)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        log("WARN", tag, message, throwable)
        logRemote(AppLogLevel.WARN, tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        log("ERROR", tag, message, throwable)
        logRemote(AppLogLevel.ERROR, tag, message, throwable)
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val cause = throwable?.let { " ($it)" }.orEmpty()
        NSLog("$level/$tag: $message$cause")
    }

    private fun logRemote(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            remoteLogger.log(level, tag, message, throwable)
        }.onFailure {
            NSLog("WARN/$tag: Remote logging failed ($it)")
        }
    }
}

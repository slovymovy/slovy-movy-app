package com.slovy.slovymovyapp.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

actual object AppLogger {
    actual var remoteLogger: AppLogSink = NoOpAppLogSink

    actual fun debug(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.d(tag, message) else Log.d(tag, message, throwable)
    }

    actual fun info(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.i(tag, message) else Log.i(tag, message, throwable)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
        logRemote(AppLogLevel.WARN, tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
        logRemote(AppLogLevel.ERROR, tag, message, throwable)
    }

    private fun logRemote(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            remoteLogger.log(level, tag, message, throwable)
        }.onFailure {
            Log.w(tag, "Remote logging failed", it)
        }
    }
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

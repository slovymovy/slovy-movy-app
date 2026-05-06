package com.slovy.slovymovyapp.logging

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

actual object AppLogger {
    actual fun debug(tag: String, message: String, throwable: Throwable?) {
        log("DEBUG", tag, message, throwable)
    }

    actual fun info(tag: String, message: String, throwable: Throwable?) {
        log("INFO", tag, message, throwable)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        log("WARN", tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        log("ERROR", tag, message, throwable)
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
}

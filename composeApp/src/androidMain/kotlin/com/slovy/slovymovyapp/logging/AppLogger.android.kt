package com.slovy.slovymovyapp.logging

import android.util.Log

actual object AppLogger {
    actual fun debug(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.d(tag, message) else Log.d(tag, message, throwable)
    }

    actual fun info(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.i(tag, message) else Log.i(tag, message, throwable)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
    }
}

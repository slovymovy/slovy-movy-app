package com.slovy.slovymovyapp.logging

expect object AppLogger {
    fun debug(tag: String, message: String, throwable: Throwable?)
    fun info(tag: String, message: String, throwable: Throwable?)
    fun warn(tag: String, message: String, throwable: Throwable?)
    fun error(tag: String, message: String, throwable: Throwable?)
}

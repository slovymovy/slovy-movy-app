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

internal class DeveloperLogBuffer(
    private val maxBytes: Int = MAX_DEVELOPER_LOG_BUFFER_BYTES,
) {
    private val entries = mutableListOf<AppLogEntry>()
    private var retainedBytes: Int = 0

    fun append(entry: AppLogEntry) {
        entries += entry
        retainedBytes += entry.estimatedBytes()
        while (retainedBytes > maxBytes && entries.isNotEmpty()) {
            retainedBytes -= entries.removeAt(0).estimatedBytes()
        }
    }

    fun clear() {
        entries.clear()
        retainedBytes = 0
    }

    fun snapshot(): List<AppLogEntry> =
        entries.toList()

    private fun AppLogEntry.estimatedBytes(): Int =
        LONG_BYTES +
            INT_BYTES +
            tag.estimatedBytes() +
            message.estimatedBytes() +
            (throwableLabel?.estimatedBytes() ?: 0)

    private fun String.estimatedBytes(): Int =
        length * BYTES_PER_CHAR

    private companion object {
        const val LONG_BYTES: Int = 8
        const val INT_BYTES: Int = 4
        const val BYTES_PER_CHAR: Int = 2
        const val MAX_DEVELOPER_LOG_BUFFER_BYTES: Int = 1024 * 128
    }
}

expect object AppLogger {
    var remoteLogger: AppLogSink
    var developerLogger: AppLogSink
    var debugLoggingEnabled: Boolean

    fun recentDeveloperLogs(): List<AppLogEntry>
    fun clearDeveloperLogs()

    fun debug(tag: String, throwable: Throwable?, message: () -> String)
    fun info(tag: String, message: String, throwable: Throwable?)
    fun warn(tag: String, message: String, throwable: Throwable?)
    fun error(tag: String, message: String, throwable: Throwable?)
}

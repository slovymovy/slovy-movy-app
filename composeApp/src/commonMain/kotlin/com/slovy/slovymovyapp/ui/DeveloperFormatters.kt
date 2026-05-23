package com.slovy.slovymovyapp.ui

import com.slovy.slovymovyapp.logging.AppLogEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val developerDateTimeFormat = LocalDateTime.Format {
    date(LocalDate.Formats.ISO)
    char(' ')
    time(LocalTime.Formats.ISO)
}

internal fun List<AppLogEntry>.developerLogSignature(): String {
    val last = lastOrNull() ?: return "0"
    return "${size}:${last.createdAtEpochMs}:${last.level}:${last.tag}:${last.message}:${last.throwableLabel}"
}

@OptIn(ExperimentalTime::class)
internal fun AppLogEntry.toDeveloperTerminalLine(): String =
    toDeveloperTerminalLine(nowEpochMs = Clock.System.now().toEpochMilliseconds())

internal fun AppLogEntry.toDeveloperTerminalLine(nowEpochMs: Long): String {
    val cause = throwableLabel?.let { " ($it)" }.orEmpty()
    return "${formatDeveloperRelativeTime(createdAtEpochMs, nowEpochMs)} ${level.name}/$tag: $message$cause"
}

internal fun formatDeveloperRelativeTime(epochMs: Long?, nowEpochMs: Long): String {
    if (epochMs == null) return "-"
    val deltaMs = epochMs - nowEpochMs
    if (kotlin.math.abs(deltaMs) < 1_000L) return "now"
    val sign = if (deltaMs < 0L) "-" else "+"
    val duration = kotlin.math.abs(deltaMs).milliseconds
    val days = duration.inWholeDays
    if (days > 0L) {
        val hours = (duration - days.days).inWholeHours
        return if (hours > 0L) "${sign}${days}d ${hours}h" else "${sign}${days}d"
    }
    val hours = duration.inWholeHours
    if (hours > 0L) {
        val minutes = (duration - hours.hours).inWholeMinutes
        return if (minutes > 0L) "${sign}${hours}h ${minutes}m" else "${sign}${hours}h"
    }
    val minutes = duration.inWholeMinutes
    if (minutes > 0L) return "${sign}${minutes}m"
    return "${sign}${duration.inWholeSeconds}s"
}

internal fun buildDeveloperDiagnosticInfo(id: String, createdAtEpochMs: Long): String {
    val localDateTime = Instant.fromEpochMilliseconds(createdAtEpochMs)
        .toLocalDateTime(currentSystemDefault())
    return "ID: $id\nAdded: ${localDateTime.format(developerDateTimeFormat)}"
}

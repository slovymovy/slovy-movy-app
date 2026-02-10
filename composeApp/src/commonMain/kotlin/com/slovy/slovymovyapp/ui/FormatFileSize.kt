package com.slovy.slovymovyapp.ui

internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> {
            val gb = (bytes * 100 / (1024 * 1024 * 1024)).toDouble() / 100.0
            "$gb GB"
        }
    }
}

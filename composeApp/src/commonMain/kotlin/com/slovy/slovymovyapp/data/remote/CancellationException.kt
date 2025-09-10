package com.slovy.slovymovyapp.data.remote

/**
 * Thrown when a download is cancelled via [CancelToken].
 * Kept in commonMain so platforms can reference the same type.
 */
class CancellationException(message: String) : Exception(message)

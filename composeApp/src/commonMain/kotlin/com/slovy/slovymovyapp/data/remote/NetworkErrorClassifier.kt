package com.slovy.slovymovyapp.data.remote

private val httpStatusRegex = Regex("""\bHTTP\s+(\d{3})\b""", RegexOption.IGNORE_CASE)

sealed class NetworkError(open val userMessage: String) {
    data object Offline : NetworkError("No internet connection. Check your connection and try again.")
    data object Timeout : NetworkError("Request timed out. Please try again.")
    data class ServerError(val statusCode: Int) : NetworkError(
        if (statusCode > 0) "Server error ($statusCode). Please try again." else "Server error. Please try again."
    )

    data object InsufficientStorage : NetworkError("Not enough free space to complete the download.")
    data object Unknown : NetworkError("Something went wrong. Please try again.")
}

object NetworkErrorClassifier {
    fun classify(throwable: Throwable): NetworkError {
        val chain = generateSequence(throwable) { it.cause }.toList()

        if (chain.any { it is NetworkOfflineException }) {
            return NetworkError.Offline
        }

        // Server errors (explicit status)
        chain.firstOrNull { it is DictionaryClientException.ServerException }?.let { t ->
            val server = t as DictionaryClientException.ServerException
            return NetworkError.ServerError(server.statusCode)
        }

        // HTTP status in message (download failures, etc.)
        chain.firstNotNullOfOrNull { extractHttpStatus(it.message) }?.let { status ->
            return NetworkError.ServerError(status)
        }

        if (chain.any { it.message?.contains("not enough free space", ignoreCase = true) == true }) {
            return NetworkError.InsufficientStorage
        }

        chain.firstOrNull { isIosOffline(it.message) }?.let {
            return NetworkError.Offline
        }

        chain.firstOrNull { isIosTimeout(it.message) }?.let {
            return NetworkError.Timeout
        }

        chain.firstOrNull { isOfflineClassName(it) }?.let {
            return NetworkError.Offline
        }

        chain.firstOrNull { isTimeoutClassName(it) }?.let {
            return NetworkError.Timeout
        }

        chain.firstOrNull { isOfflineMessage(it.message) }?.let {
            return NetworkError.Offline
        }

        chain.firstOrNull { isTimeoutMessage(it.message) }?.let {
            return NetworkError.Timeout
        }

        return NetworkError.Unknown
    }

    fun userMessage(throwable: Throwable): String = classify(throwable).userMessage

    private fun extractHttpStatus(message: String?): Int? {
        if (message.isNullOrBlank()) return null
        val match = httpStatusRegex.find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun isOfflineClassName(throwable: Throwable): Boolean {
        return when (throwable::class.simpleName) {
            "UnknownHostException",
            "ConnectException",
            "NoRouteToHostException" -> true

            else -> false
        }
    }

    private fun isTimeoutClassName(throwable: Throwable): Boolean {
        return when (throwable::class.simpleName) {
            "SocketTimeoutException",
            "HttpRequestTimeoutException",
            "ConnectTimeoutException" -> true

            else -> false
        }
    }

    private fun isOfflineMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains("no internet connection") ||
                m.contains("no internet") ||
                m.contains("network is unreachable") ||
                m.contains("unable to resolve host")
    }

    private fun isTimeoutMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains("timed out") || m.contains("timeout")
    }

    private fun isIosOffline(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains("not connected to the internet") ||
                (m.contains("nsurlerrordomain") && m.contains("-1009")) ||
                m.contains("could not connect to the server") ||
                (m.contains("nsurlerrordomain") && m.contains("-1004")) ||
                m.contains("the network connection was lost") ||
                (m.contains("nsurlerrordomain") && m.contains("-1005")) ||
                m.contains("a server with the specified hostname could not be found") ||
                (m.contains("nsurlerrordomain") && m.contains("-1003"))
    }

    private fun isIosTimeout(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains("the request timed out") ||
                (m.contains("nsurlerrordomain") && m.contains("-1001"))
    }
}

class NetworkOfflineException : Exception("No internet connection")

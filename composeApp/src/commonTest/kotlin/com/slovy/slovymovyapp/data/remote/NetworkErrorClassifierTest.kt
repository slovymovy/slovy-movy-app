package com.slovy.slovymovyapp.data.remote

import kotlin.test.Test
import kotlin.test.assertTrue

private class UnknownHostException : Exception()
private class ConnectException : Exception()
private class NoRouteToHostException : Exception()
private class UnresolvedAddressException : Exception()
private class SocketTimeoutException : Exception()
private class HttpRequestTimeoutException : Exception()
private class ConnectTimeoutException : Exception()

class NetworkErrorClassifierTest {
    @Test
    fun classifies_offline_by_class_name() {
        assertTrue(NetworkErrorClassifier.classify(UnknownHostException()) is NetworkError.Offline)
        assertTrue(NetworkErrorClassifier.classify(ConnectException()) is NetworkError.Offline)
        assertTrue(NetworkErrorClassifier.classify(NoRouteToHostException()) is NetworkError.Offline)
        assertTrue(NetworkErrorClassifier.classify(UnresolvedAddressException()) is NetworkError.Offline)
    }

    @Test
    fun classifies_timeout_by_class_name() {
        assertTrue(NetworkErrorClassifier.classify(SocketTimeoutException()) is NetworkError.Timeout)
        assertTrue(NetworkErrorClassifier.classify(HttpRequestTimeoutException()) is NetworkError.Timeout)
        assertTrue(NetworkErrorClassifier.classify(ConnectTimeoutException()) is NetworkError.Timeout)
    }

    @Test
    fun classifies_server_error_from_exception() {
        val error = DictionaryClientException.ServerException(503, "oops")
        assertTrue(NetworkErrorClassifier.classify(error) is NetworkError.ServerError)
    }

    @Test
    fun classifies_server_error_from_http_message() {
        val error = IllegalStateException("HTTP 500 Internal Server Error while downloading https://example.com")
        assertTrue(NetworkErrorClassifier.classify(error) is NetworkError.ServerError)
    }

    @Test
    fun classifies_insufficient_storage() {
        val error = IllegalStateException("Not enough free space to download file")
        assertTrue(NetworkErrorClassifier.classify(error) is NetworkError.InsufficientStorage)
    }

    @Test
    fun classifies_ios_offline_messages() {
        val error = Exception("NSURLErrorDomain -1009")
        assertTrue(NetworkErrorClassifier.classify(error) is NetworkError.Offline)
    }

    @Test
    fun classifies_timeout_messages() {
        val error = Exception("The request timed out")
        assertTrue(NetworkErrorClassifier.classify(error) is NetworkError.Timeout)
    }

    @Test
    fun classifies_cause_chain() {
        val error = Exception("wrapper", UnknownHostException())
        assertTrue(NetworkErrorClassifier.classify(error) is NetworkError.Offline)
    }
}

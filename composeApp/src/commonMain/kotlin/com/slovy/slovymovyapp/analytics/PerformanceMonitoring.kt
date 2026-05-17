package com.slovy.slovymovyapp.analytics

interface PerformanceTrace {
    fun putAttribute(name: String, value: String)
    fun putMetric(name: String, value: Long)
    fun incrementMetric(name: String, by: Long = 1L)
    fun stop()
}

interface PerformanceMonitor {
    fun startTrace(name: String): PerformanceTrace
}

object NoOpPerformanceMonitor : PerformanceMonitor {
    override fun startTrace(name: String): PerformanceTrace = NoOpPerformanceTrace
}

object NoOpPerformanceTrace : PerformanceTrace {
    override fun putAttribute(name: String, value: String) = Unit
    override fun putMetric(name: String, value: Long) = Unit
    override fun incrementMetric(name: String, by: Long) = Unit
    override fun stop() = Unit
}

expect object PerformanceMonitoring {
    /**
     * Routes custom trace calls. Defaults to [NoOpPerformanceMonitor] so tests and
     * unsupported platforms do not need a Firebase SDK.
     */
    var monitor: PerformanceMonitor

    fun startTrace(name: String): PerformanceTrace
}

fun PerformanceTrace.putAttributes(attributes: Map<String, Any?>) {
    attributes.forEach { (name, value) ->
        if (value != null) putAttribute(name, value.toString().take(MAX_FIREBASE_ATTRIBUTE_LENGTH))
    }
}

private const val MAX_FIREBASE_ATTRIBUTE_LENGTH = 100

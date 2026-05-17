package com.slovy.slovymovyapp.analytics

import kotlin.coroutines.cancellation.CancellationException

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

object PerformanceMonitoring {
    /**
     * Routes custom trace calls. Defaults to [NoOpPerformanceMonitor] so tests and
     * unsupported platforms do not need a Firebase SDK. Production entry points
     * (e.g. MainActivity on Android) install the real monitor at startup.
     */
    var monitor: PerformanceMonitor = NoOpPerformanceMonitor

    fun startTrace(name: String): PerformanceTrace = monitor.startTrace(name)
}

inline fun <T> PerformanceTrace.use(block: (PerformanceTrace) -> T): T {
    try {
        return block(this)
    } finally {
        stop()
    }
}

class PerformanceTraceScope @PublishedApi internal constructor(
    private val trace: PerformanceTrace,
    private val resultAttribute: String,
    defaultResult: String,
) : PerformanceTrace by trace {
    private var result: String = defaultResult
    private var explicitResult: Boolean = false

    fun markResult(result: String) {
        this.result = result
        explicitResult = true
    }

    @PublishedApi
    internal fun finish() {
        trace.putAttribute(resultAttribute, result)
    }

    @PublishedApi
    internal fun markResultIfUnset(result: String) {
        if (!explicitResult) {
            this.result = result
        }
    }
}

inline fun <T> PerformanceTrace.useWithResult(
    resultAttribute: String = "result",
    successResult: String = "success",
    block: PerformanceTraceScope.() -> T,
): T {
    val scope = PerformanceTraceScope(this, resultAttribute, successResult)
    try {
        return scope.block()
    } catch (e: CancellationException) {
        scope.markResultIfUnset("cancelled")
        throw e
    } catch (e: Throwable) {
        scope.markResultIfUnset("failed")
        throw e
    } finally {
        scope.finish()
        stop()
    }
}

fun PerformanceTrace.putAttributes(attributes: Map<String, Any?>) {
    attributes.forEach { (name, value) ->
        if (value != null) putAttribute(name, value.toString().take(MAX_FIREBASE_ATTRIBUTE_LENGTH))
    }
}

private const val MAX_FIREBASE_ATTRIBUTE_LENGTH = 100

package com.slovy.slovymovyapp.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

actual object PerformanceMonitoring {
    actual var monitor: PerformanceMonitor = NoOpPerformanceMonitor

    actual fun startTrace(name: String): PerformanceTrace = monitor.startTrace(name)
}

class FirebasePerformanceMonitor : PerformanceMonitor {
    override fun startTrace(name: String): PerformanceTrace {
        val trace = FirebasePerformance.getInstance().newTrace(name)
        trace.start()
        return FirebasePerformanceTrace(trace)
    }
}

private class FirebasePerformanceTrace(
    private val trace: Trace,
) : PerformanceTrace {
    override fun putAttribute(name: String, value: String) {
        trace.putAttribute(name, value)
    }

    override fun putMetric(name: String, value: Long) {
        trace.putMetric(name, value)
    }

    override fun incrementMetric(name: String, by: Long) {
        trace.incrementMetric(name, by)
    }

    override fun stop() {
        trace.stop()
    }
}

package com.slovy.slovymovyapp.analytics

actual object PerformanceMonitoring {
    actual var monitor: PerformanceMonitor = NoOpPerformanceMonitor

    actual fun startTrace(name: String): PerformanceTrace = monitor.startTrace(name)
}

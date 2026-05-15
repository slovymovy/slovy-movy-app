package com.slovy.slovymovyapp.analytics

interface AnalyticsLogger {
    fun logEvent(name: String, params: Map<String, Any>)
}

object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
}

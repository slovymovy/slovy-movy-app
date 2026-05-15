package com.slovy.slovymovyapp.analytics

interface AnalyticsLogger {
    fun logEvent(name: String, params: Map<String, Any>)
    fun setUserProperty(name: String, value: String?)
}

object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
}

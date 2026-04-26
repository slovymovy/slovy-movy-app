package com.slovy.slovymovyapp.analytics

interface AnalyticsLogger {
    fun logEvent(name: String, params: Map<String, String>)
}

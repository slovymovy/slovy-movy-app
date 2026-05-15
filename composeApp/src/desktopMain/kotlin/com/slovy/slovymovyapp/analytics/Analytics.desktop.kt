package com.slovy.slovymovyapp.analytics

actual object Analytics {
    actual fun logEvent(event: AnalyticsEvent, params: Map<String, Any>) = Unit
}

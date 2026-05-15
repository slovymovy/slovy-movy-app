package com.slovy.slovymovyapp.analytics

actual object Analytics {
    lateinit var logger: AnalyticsLogger

    actual fun logEvent(event: AnalyticsEvent, params: Map<String, Any>) {
        logger.logEvent(event.name.lowercase(), params)
    }
}

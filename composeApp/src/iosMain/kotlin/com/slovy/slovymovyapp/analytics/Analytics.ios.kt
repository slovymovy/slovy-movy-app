package com.slovy.slovymovyapp.analytics

actual object Analytics {
    actual var logger: AnalyticsLogger = NoOpAnalyticsLogger

    actual fun logEvent(event: AnalyticsEvent, params: Map<String, Any>) {
        logger.logEvent(event.name.lowercase(), params)
    }
}

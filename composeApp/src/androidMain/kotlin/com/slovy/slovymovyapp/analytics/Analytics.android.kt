package com.slovy.slovymovyapp.analytics

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics


actual object Analytics {
    actual var logger: AnalyticsLogger = NoOpAnalyticsLogger

    actual fun logEvent(event: AnalyticsEvent, params: Map<String, Any>) {
        logger.logEvent(event.name.lowercase(), params)
    }
}

class FirebaseAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(name: String, params: Map<String, Any>) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
        Firebase.analytics.logEvent(name, bundle)
    }
}

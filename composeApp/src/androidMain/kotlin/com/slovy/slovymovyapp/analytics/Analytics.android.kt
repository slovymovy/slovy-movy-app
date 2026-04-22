package com.slovy.slovymovyapp.analytics

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics


actual object Analytics {
    actual fun logEvent(event: AnalyticsEvent, params: Map<String, String>) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) -> putString(key, value) }
        }
        Firebase.analytics.logEvent(event.name.lowercase(), bundle)
    }
}

package com.slovy.slovymovyapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.FirebaseAnalyticsLogger
import com.slovy.slovymovyapp.analytics.FirebaseAppCheckInstaller
import com.slovy.slovymovyapp.analytics.FirebasePerformanceMonitor
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.androidApp.BuildConfig
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.logging.FirebaseCrashlyticsAppLogSink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        FirebaseAppCheckInstaller.install(isDebug = BuildConfig.DEBUG)
        Analytics.logger = FirebaseAnalyticsLogger()
        PerformanceMonitoring.monitor = FirebasePerformanceMonitor()
        AppLogger.remoteLogger = FirebaseCrashlyticsAppLogSink()

        val platform = PlatformDbSupport(this)
        val buildConfig = AppBuildConfig(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            isDebug = BuildConfig.DEBUG,
            applicationId = BuildConfig.APPLICATION_ID
        )
        setContent {
            AppRoot(platform, buildConfig, this)
        }
    }
}

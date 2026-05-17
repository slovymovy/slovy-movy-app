package com.slovy.slovymovyapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.FirebaseAnalyticsLogger
import com.slovy.slovymovyapp.analytics.FirebasePerformanceMonitor
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.androidApp.BuildConfig
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.logging.FirebaseCrashlyticsAppLogSink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Analytics.logger = FirebaseAnalyticsLogger()
        PerformanceMonitoring.monitor = FirebasePerformanceMonitor()
        AppLogger.remoteLogger = FirebaseCrashlyticsAppLogSink()

        setContent {
            val platform = PlatformDbSupport(this)
            val db = DataDbManager.openAppDatabase(platform)
            val settingRepo = SettingsRepository(db)
            val dataDbManager = DataDbManager(platform, settingRepo, GoogleStorageBucketDataProvider())
            val buildConfig = AppBuildConfig(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                isDebug = BuildConfig.DEBUG,
                applicationId = BuildConfig.APPLICATION_ID
            )
            App(settingRepo, dataDbManager, platform, buildConfig, this)
        }
    }
}

package com.slovy.slovymovyapp

import android.content.Intent
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
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.logging.FirebaseCrashlyticsAppLogSink
import com.slovy.slovymovyapp.share.AndroidSharedTextIntent
import com.slovy.slovymovyapp.share.SharedTextReceiver

class MainActivity : ComponentActivity() {
    // Text handed over by ACTION_PROCESS_TEXT / ACTION_SEND. Owned by the activity so both
    // the creating intent and later onNewIntent deliveries reach the same composition.
    private val sharedTextReceiver = SharedTextReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        FirebaseAppCheckInstaller.install(isDebug = BuildConfig.DEBUG)
        Analytics.logger = FirebaseAnalyticsLogger()
        PerformanceMonitoring.monitor = FirebasePerformanceMonitor()
        AppLogger.remoteLogger = FirebaseCrashlyticsAppLogSink()
        // Only a fresh launch carries the shared text; after a process restart the restored
        // activity would otherwise re-deliver the original selection on every recreation.
        if (savedInstanceState == null) {
            offerSharedText(intent)
        }

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
            App(settingRepo, dataDbManager, platform, buildConfig, this, sharedTextReceiver)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerSharedText(intent)
    }

    private fun offerSharedText(intent: Intent?) {
        val (text, source) = AndroidSharedTextIntent.read(intent) ?: return
        sharedTextReceiver.offer(text, source)
    }
}

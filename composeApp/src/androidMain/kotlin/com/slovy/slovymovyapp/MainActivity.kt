package com.slovy.slovymovyapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val platform = PlatformDbSupport(this)
            val db = DataDbManager.openAppDatabase(platform)
            val settingRepo = SettingsRepository(db)
            val dataDbManager = DataDbManager(platform, settingRepo, GoogleStorageBucketDataProvider())
            App(settingRepo, dataDbManager, platform, this)
        }
    }
}

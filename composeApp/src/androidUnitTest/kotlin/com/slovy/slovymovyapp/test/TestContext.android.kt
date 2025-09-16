package com.slovy.slovymovyapp.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

actual object TestContext {
    actual fun androidContext(): Any? {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val platformDbSupport = PlatformDbSupport(applicationContext)
        val databasePath = platformDbSupport.getDatabasePath("anything.db").parent.toString()
        ShadowStatFs.registerStats(databasePath, 1000000000, 1000000, 100000)
        return applicationContext
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
actual abstract class BaseTest actual constructor()
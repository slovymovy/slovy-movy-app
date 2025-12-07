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

    actual fun getCiEnv(name: String): String? {
        val env = System.getenv(name)
        return if (env.isNullOrEmpty()) null else env
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
actual abstract class BaseTest actual constructor() : BaseTestImpl()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreIos actual constructor()
package com.slovy.slovymovyapp.test

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slovy.slovymovyapp.data.export.AppDataExportResult
import org.junit.runner.RunWith

actual object TestContext {
    actual fun androidContext(): Any? {
        return ApplicationProvider.getApplicationContext()
    }

    actual fun getCiEnv(name: String): String? {
        val env = InstrumentationRegistry.getArguments().getCharSequence(name) as String?
        return if (env.isNullOrEmpty()) null else env
    }

    actual fun testServerHost(): String = "10.0.2.2"

    actual suspend fun runInExportTestEnvironment(block: suspend () -> Unit) {
        AndroidExportTestSupport.runInExportTestEnvironment(
            ApplicationProvider.getApplicationContext(),
            block
        )
    }

    actual fun exportArtifactExists(result: AppDataExportResult): Boolean {
        return AndroidExportTestSupport.exportArtifactExists(
            ApplicationProvider.getApplicationContext(),
            result
        )
    }

    actual fun deleteExportArtifact(result: AppDataExportResult) {
        AndroidExportTestSupport.deleteExportArtifact(
            ApplicationProvider.getApplicationContext(),
            result
        )
    }
}

@RunWith(AndroidJUnit4::class)
actual abstract class BaseTest actual constructor() : BaseTestImpl()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreIos actual constructor()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreRobolectric actual constructor()

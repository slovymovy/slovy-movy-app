package com.slovy.slovymovyapp.test

import com.slovy.slovymovyapp.data.export.AppDataExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }

    actual fun getCiEnv(name: String): String? {
        val env = System.getenv(name)
        return if (env.isNullOrEmpty()) null else env
    }

    actual fun testServerHost(): String = "127.0.0.1"

    actual suspend fun runInExportTestEnvironment(block: suspend () -> Unit) {
        val originalHome = System.getProperty("user.home")
        val tempHome = withContext(Dispatchers.IO) {
            Files.createTempDirectory("slovymovy-export-test-")
        }.toFile()
        try {
            System.setProperty("user.home", tempHome.absolutePath)
            block()
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", originalHome)
            }
            tempHome.deleteRecursively()
        }
    }

    actual fun exportArtifactExists(result: AppDataExportResult): Boolean {
        return File(result.destinationLabel).exists()
    }

    actual fun deleteExportArtifact(result: AppDataExportResult) {
        File(result.destinationLabel).delete()
    }
}

actual abstract class BaseTest actual constructor() : BaseTestImpl()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreIos actual constructor()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreRobolectric actual constructor()

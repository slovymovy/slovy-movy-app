package com.slovy.slovymovyapp.test

import com.slovy.slovymovyapp.data.export.AppDataExportResult
import com.slovy.slovymovyapp.data.export.standardAppDataFileNamesWithSidecars
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.*
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getCiEnv(name: String): String? {
        val env = getenv(name)?.toKString()
        return if (env.isNullOrEmpty()) null else env
    }

    actual fun testServerHost(): String = "127.0.0.1"

    actual suspend fun runInExportTestEnvironment(block: suspend () -> Unit) {
        clearDatabaseFiles()
        clearExportArtifacts()
        try {
            block()
        } finally {
            clearDatabaseFiles()
            clearExportArtifacts()
        }
    }

    actual fun exportArtifactExists(result: AppDataExportResult): Boolean {
        return NSFileManager.defaultManager.fileExistsAtPath("${exportsRoot()}/${result.artifactName}")
    }

    actual fun deleteExportArtifact(result: AppDataExportResult) {
        NSFileManager.defaultManager.removeItemAtPath("${exportsRoot()}/${result.artifactName}", error = null)
    }

    private fun clearExportArtifacts() {
        val root = exportsRoot()
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(root)) {
            fileManager.removeItemAtPath(root, error = null)
        }
    }

    private fun clearDatabaseFiles() {
        val root = databasesRoot()
        val fileManager = NSFileManager.defaultManager
        standardAppDataFileNamesWithSidecars.forEach { name ->
            fileManager.removeItemAtPath("$root/$name", error = null)
        }
    }

    private fun databasesRoot(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        val dir = (paths as NSArray).objectAtIndex(0u) as? String ?: NSHomeDirectory()
        return "$dir/databases"
    }

    private fun exportsRoot(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val dir = (paths as NSArray).objectAtIndex(0u) as? String ?: NSHomeDirectory()
        return "$dir/SlovyMovy"
    }
}

actual abstract class BaseTest actual constructor() : BaseTestImpl()

actual typealias IgnoreIos = kotlin.test.Ignore

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreRobolectric actual constructor()

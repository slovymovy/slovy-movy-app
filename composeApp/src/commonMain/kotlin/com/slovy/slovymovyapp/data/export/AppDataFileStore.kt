package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileInput
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import kotlinx.io.files.Path

/**
 * File operations inside the app's databases directory, addressed by bare file name. The import
 * applier works only through this seam so tests can inject a failing move and prove rollback.
 */
internal interface AppDataFileStore {
    fun path(name: String): Path
    fun exists(name: String): Boolean
    fun size(name: String): Long?
    fun listNames(): List<String>
    fun openInput(name: String): PlatformFileInput
    fun openOutput(name: String): PlatformFileOutput
    fun copy(from: String, to: String): Boolean
    fun move(from: String, to: String): Boolean
    fun delete(name: String)
}

internal class PlatformAppDataFileStore(private val platform: PlatformDbSupport) : AppDataFileStore {
    override fun path(name: String): Path = platform.getDatabasePath(name)

    override fun exists(name: String): Boolean = platform.fileExists(path(name))

    override fun size(name: String): Long? = platform.getFileSize(path(name))

    override fun listNames(): List<String> {
        val databasesDir = platform.getDatabasePath("")
        if (!platform.fileExists(databasesDir)) return emptyList()
        return platform.listFiles(databasesDir).map { it.name }
    }

    override fun openInput(name: String): PlatformFileInput = platform.openInput(path(name))

    override fun openOutput(name: String): PlatformFileOutput {
        platform.ensureDatabasesDir()
        return platform.openOutput(path(name))
    }

    override fun copy(from: String, to: String): Boolean = platform.copyFile(path(from), path(to))

    override fun move(from: String, to: String): Boolean = platform.moveFile(path(from), path(to))

    override fun delete(name: String) {
        platform.deleteFile(path(name))
    }
}

package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import kotlinx.io.files.Path

internal data class AppDataArchiveEntry(
    val name: String,
    val sizeBytes: Long,
    val writeContentsTo: (PlatformFileOutput) -> Unit
)

internal val standardAppDataDatabaseFileNames: List<String> = listOf(
    "app.db",
    LocalDbManager.LOCAL_DICTIONARY_FILENAME,
    LocalDbManager.LOCAL_TRANSLATION_FILENAME
)

internal val standardAppDataFileNamesWithSidecars: List<String> =
    standardAppDataDatabaseFileNames.flatMap { name ->
        listOf(name, "$name-wal", "$name-shm")
    }

internal fun writeTarArchive(
    output: PlatformFileOutput,
    entries: List<AppDataArchiveEntry>,
    lastModifiedEpochSeconds: Long
) {
    try {
        entries.forEach { entry ->
            val header = TarArchive.headerBytes(
                name = entry.name,
                size = entry.sizeBytes,
                lastModifiedEpochSeconds = lastModifiedEpochSeconds
            )
            output.write(header, offset = 0, length = header.size)
            entry.writeContentsTo(output)
            val padding = TarArchive.paddingBytes(entry.sizeBytes)
            if (padding.isNotEmpty()) {
                output.write(padding, offset = 0, length = padding.size)
            }
        }
        val endOfArchive = TarArchive.endOfArchiveBytes()
        output.write(endOfArchive, offset = 0, length = endOfArchive.size)
        output.flush()
    } finally {
        output.close()
    }
}

internal fun writeTarArchiveToPath(
    platform: PlatformDbSupport,
    destinationPath: Path,
    entries: List<AppDataArchiveEntry>,
    lastModifiedEpochSeconds: Long
) {
    val tempPath = Path("${destinationPath}.part")
    try {
        if (platform.fileExists(tempPath)) {
            platform.deleteFile(tempPath)
        }
        writeTarArchive(
            output = platform.openOutput(tempPath),
            entries = entries,
            lastModifiedEpochSeconds = lastModifiedEpochSeconds
        )
        if (!platform.moveFile(tempPath, destinationPath)) {
            platform.deleteFile(tempPath)
            error("Failed to move export archive to destination.")
        }
    } catch (t: Throwable) {
        platform.deleteFile(tempPath)
        throw t
    }
}

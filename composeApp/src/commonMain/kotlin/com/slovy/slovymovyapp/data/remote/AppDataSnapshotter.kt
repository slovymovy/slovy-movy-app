package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.local.LocalDbManager
import kotlinx.io.files.Path
import kotlin.random.Random

internal data class AppDataSnapshot(
    val archiveName: String,
    val path: Path
)

internal val standardAppDataDatabaseFileNames: List<String> = listOf(
    "app.db",
    LocalDbManager.LOCAL_DICTIONARY_FILENAME,
    LocalDbManager.LOCAL_TRANSLATION_FILENAME
)

internal fun appDataFileNameWithSidecars(name: String): List<String> =
    listOf(name, "$name-wal", "$name-shm")

internal val standardAppDataFileNamesWithSidecars: List<String> =
    standardAppDataDatabaseFileNames.flatMap { appDataFileNameWithSidecars(it) }

internal inline fun <T> withAppDataSnapshots(
    platform: PlatformDbSupport,
    block: (List<AppDataSnapshot>) -> T
): T {
    val snapshots = createAppDataSnapshots(platform)
    try {
        return block(snapshots)
    } finally {
        snapshots.forEach { snapshot ->
            platform.deleteFile(snapshot.path)
        }
    }
}

private fun createAppDataSnapshots(platform: PlatformDbSupport): List<AppDataSnapshot> {
    val snapshotPrefix = "slovymovy-export-snapshot-${Random.nextInt(1_000_000_000)}"
    val snapshots = mutableListOf<AppDataSnapshot>()
    try {
        standardAppDataDatabaseFileNames.forEach { fileName ->
            val sourcePath = platform.getDatabasePath(fileName)
            if (!platform.fileExists(sourcePath)) return@forEach

            val snapshotPath = platform.getDatabasePath("$snapshotPrefix-$fileName")
            if (platform.fileExists(snapshotPath)) {
                platform.deleteFile(snapshotPath)
            }
            createDatabaseSnapshot(platform, fileName, sourcePath, snapshotPath)
            snapshots += AppDataSnapshot(
                archiveName = fileName,
                path = snapshotPath
            )
        }
        return snapshots
    } catch (t: Throwable) {
        snapshots.forEach { snapshot ->
            platform.deleteFile(snapshot.path)
        }
        throw t
    }
}

private fun createDatabaseSnapshot(
    platform: PlatformDbSupport,
    fileName: String,
    sourcePath: Path,
    snapshotPath: Path
) {
    val driver = openExportDriver(platform, fileName, sourcePath)
    try {
        try {
            driver.execute(
                identifier = null,
                sql = "VACUUM INTO ${snapshotPath.toSqliteStringLiteral()}",
                parameters = 0
            )
        } catch (t: Throwable) {
            if (!isVacuumIntoUnsupported(t)) {
                throw t
            }
            createCheckpointCopySnapshot(driver, platform, sourcePath, snapshotPath)
        }
    } catch (t: Throwable) {
        platform.deleteFile(snapshotPath)
        throw t
    } finally {
        driver.close()
    }
}

private fun openExportDriver(
    platform: PlatformDbSupport,
    fileName: String,
    path: Path
): SqlDriver = when (fileName) {
    "app.db" -> platform.createAppDataDriver(path)
    LocalDbManager.LOCAL_DICTIONARY_FILENAME -> platform.createDictionaryDataDriver(path, readOnly = false)
    LocalDbManager.LOCAL_TRANSLATION_FILENAME -> platform.createTranslationDataDriver(path, readOnly = false)
    else -> error("Unsupported app data export file: $fileName")
}

private fun Path.toSqliteStringLiteral(): String =
    "'${toString().replace("'", "''")}'"

private fun createCheckpointCopySnapshot(
    driver: SqlDriver,
    platform: PlatformDbSupport,
    sourcePath: Path,
    snapshotPath: Path
) {
    try {
        driver.execute(
            identifier = null,
            sql = "PRAGMA wal_checkpoint(FULL)",
            parameters = 0
        )
    } catch (_: Throwable) {
        // best effort; continue with a direct file copy
    }
    if (!platform.copyFile(sourcePath, snapshotPath)) {
        error("Failed to copy database snapshot to temporary export file.")
    }
}

private fun isVacuumIntoUnsupported(error: Throwable): Boolean {
    val message = error.message?.lowercase() ?: return false
    return ("vacuum into" in message) ||
        ("near \"into\"" in message && "syntax error" in message) ||
        ("near 'into'" in message && "syntax error" in message)
}

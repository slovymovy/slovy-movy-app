package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.AppDataSnapshot
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import com.slovy.slovymovyapp.data.remote.withAppDataSnapshots
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import platform.Foundation.*
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AppDataExporter actual constructor(androidContext: Any?) {
    private val fileManager = NSFileManager.defaultManager
    private val platformDbSupport = PlatformDbSupport()
    private val databasesDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        val dir = (paths as NSArray).objectAtIndex(0u) as? String ?: NSHomeDirectory()
        "$dir/databases"
    }
    private val exportsRoot: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val dir = (paths as NSArray).objectAtIndex(0u) as? String ?: NSHomeDirectory()
        val full = "$dir/SlovyMovy"
        ensureDir(full)
        full
    }

    actual val isSupported: Boolean = true
    actual val destinationDescription: String = "Documents/SlovyMovy"
    actual val canShareExport: Boolean = true

    actual suspend fun exportAppData(): AppDataExportResult = withContext(Dispatchers.Default) {
        val formatter = NSDateFormatter().apply {
            dateFormat = "yyyyMMdd-HHmmss"
        }
        val fileName = "slovymovy-db-export-${formatter.stringFromDate(NSDate())}.tar"
        val outputPath = "$exportsRoot/$fileName"
        withAppDataSnapshots(platformDbSupport) { snapshots ->
            require(snapshots.isNotEmpty()) { "No database files found to export." }

            var writeSucceeded = false
            try {
                writeTarArchiveToPath(
                    platform = platformDbSupport,
                    destinationPath = Path(outputPath),
                    entries = archiveEntries(snapshots),
                    lastModifiedEpochSeconds = NSDate().timeIntervalSince1970.toLong()
                )
                writeSucceeded = true
            } finally {
                if (!writeSucceeded) {
                    fileManager.removeItemAtPath(outputPath, error = null)
                }
            }

            AppDataExportResult(
                artifactName = fileName,
                destinationLabel = "Documents/SlovyMovy/$fileName",
                shareReference = outputPath
            )
        }
    }

    actual fun shareExport(result: AppDataExportResult) {
        val shareReference = result.shareReference ?: error("Missing share reference for export.")
        val presenter = topViewController() ?: error("No iOS view controller available for sharing.")
        val fileUrl = NSURL.fileURLWithPath(shareReference)
        val shareSheet = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )
        shareSheet.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(shareSheet, animated = true, completion = null)
    }

    private fun archiveEntries(snapshots: List<AppDataSnapshot>): List<AppDataArchiveEntry> =
        snapshots.map { snapshot ->
            val sourcePath = snapshot.path.toString()
            AppDataArchiveEntry(
                name = snapshot.archiveName,
                sizeBytes = fileSize(sourcePath) ?: error("Failed to determine size for ${snapshot.archiveName}"),
                writeContentsTo = { output ->
                    copyFileToOutput(sourcePath, output)
                }
            )
        }

    private fun fileSize(path: String): Long? {
        val attrs = fileManager.attributesOfItemAtPath(path, error = null)
        return (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue
    }

    private fun copyFileToOutput(
        sourcePath: String,
        output: PlatformFileOutput
    ) {
        val input = NSInputStream.inputStreamWithFileAtPath(sourcePath)
            ?: error("Failed to open $sourcePath for reading")
        val buffer = ByteArray(8 * 1024)
        input.open()
        try {
            while (true) {
                val bytesRead = buffer.usePinned { pinned ->
                    input.read(
                        pinned.addressOf(0).reinterpret(),
                        buffer.size.toULong()
                    ).toInt()
                }
                when {
                    bytesRead > 0 -> output.write(buffer, offset = 0, length = bytesRead)
                    bytesRead == 0 -> break
                    else -> error("Failed to read $sourcePath")
                }
            }
        } finally {
            input.close()
        }
    }

    private fun ensureDir(path: String) {
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(path, true, null, null)
        }
    }

    private fun topViewController(): UIViewController? {
        val controller = activeWindow()
            ?.rootViewController
            ?: UIApplication.sharedApplication.keyWindow?.rootViewController
        var current = controller
        while (current?.presentedViewController != null) {
            current = current.presentedViewController
        }
        return current
    }

    private fun activeWindow(): UIWindow? {
        val scenes = UIApplication.sharedApplication.connectedScenes
            .mapNotNull { it as? UIWindowScene }
        val foregroundScenes = scenes.filter { scene ->
            scene.activationState == UISceneActivationStateForegroundActive
        }
        val windows = (foregroundScenes.ifEmpty { scenes }).flatMap { scene ->
            scene.windows.mapNotNull { it as? UIWindow }
        }
        return windows.firstOrNull { window -> window.isKeyWindow() } ?: windows.firstOrNull()
    }
}

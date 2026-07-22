package com.slovy.slovymovyapp.data.export

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class WordListFileSaver actual constructor(androidContext: Any?) {
    private val fileManager = NSFileManager.defaultManager
    private val exportsRoot: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val dir = (paths as NSArray).objectAtIndex(0u) as? String ?: NSHomeDirectory()
        val full = "$dir/OpenWords"
        if (!fileManager.fileExistsAtPath(full)) {
            fileManager.createDirectoryAtPath(full, true, null, null)
        }
        full
    }

    actual val isSupported: Boolean = true
    actual val canShare: Boolean = true

    actual suspend fun save(fileName: String, mimeType: String, content: String): WordListSaveResult =
        withContext(Dispatchers.Default) {
            val outputPath = "$exportsRoot/$fileName"
            val written = NSString.create(string = content).writeToFile(
                outputPath,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            )
            if (!written) error("Failed to write $fileName")
            WordListSaveResult(
                fileName = fileName,
                destinationLabel = "Documents/OpenWords/$fileName",
                shareReference = outputPath,
            )
        }

    actual fun share(result: WordListSaveResult, mimeType: String) {
        val shareReference = result.shareReference ?: error("Missing share reference for export.")
        presentFileShareSheet(shareReference)
    }
}

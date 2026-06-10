package com.slovy.slovymovyapp.data.export

actual class AppDataImporter actual constructor(androidContext: Any?) {
    actual val isSupported: Boolean = false
    actual val sourceDescription: String = "Import is not available on iOS yet"

    actual suspend fun stageAppDataImport(): AppDataImportStageResult? {
        error(sourceDescription)
    }
}

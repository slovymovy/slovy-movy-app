package com.slovy.slovymovyapp.data.export

expect class AppDataImporter(androidContext: Any? = null) {
    val isSupported: Boolean
    val sourceDescription: String

    /** Returns null when the user dismissed the picker without choosing a file. */
    suspend fun stageAppDataImport(): AppDataImportStageResult?
}

data class AppDataImportStageResult(
    val artifactName: String,
    val sourceLabel: String,
)

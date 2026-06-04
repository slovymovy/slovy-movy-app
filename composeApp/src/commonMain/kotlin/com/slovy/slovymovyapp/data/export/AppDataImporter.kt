package com.slovy.slovymovyapp.data.export

expect class AppDataImporter(androidContext: Any? = null) {
    val isSupported: Boolean
    val sourceDescription: String

    suspend fun stageAppDataImport(): AppDataImportStageResult
}

data class AppDataImportStageResult(
    val artifactName: String,
    val sourceLabel: String,
)

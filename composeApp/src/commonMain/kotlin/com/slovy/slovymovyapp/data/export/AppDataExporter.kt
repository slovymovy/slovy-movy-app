package com.slovy.slovymovyapp.data.export

expect class AppDataExporter(androidContext: Any? = null) {
    val isSupported: Boolean
    val destinationDescription: String
    val canShareExport: Boolean

    suspend fun exportAppData(): AppDataExportResult
    fun shareExport(result: AppDataExportResult)
}

data class AppDataExportResult(
    val artifactName: String,
    val destinationLabel: String,
    val shareReference: String? = null
)

package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.TestContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class AppDataExporterTest : BaseTest() {

    @Test
    fun exportAppData_creates_export_artifact() = runTest {
        TestContext.runInExportTestEnvironment {
            val appDb = testAppDatabaseHolder().database
            SettingsRepository(appDb).insert(
                Setting(
                    id = Setting.Name.TEST_PROPERTY,
                    value = JsonPrimitive("export-test")
                )
            )

            val exporter = AppDataExporter(TestContext.androidContext())
            assertTrue(exporter.isSupported, "Exporter should be supported on this platform")

            val result = exporter.exportAppData()
            try {
                assertTrue(
                    TestContext.exportArtifactExists(result),
                    "Export should create an artifact"
                )
            } finally {
                TestContext.deleteExportArtifact(result)
            }
        }
    }
}

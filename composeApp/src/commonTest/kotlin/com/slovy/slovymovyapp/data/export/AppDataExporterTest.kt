package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.TestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AppDataExporterTest : BaseTest() {

    @Test
    fun exportAppData_creates_export_artifact() = runTest {
        TestContext.runInExportTestEnvironment {
            // Creating the app DB through the normal test database holder ensures the exporter sees
            // a real database created through the same code path as production.
            testAppDatabaseHolder()

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

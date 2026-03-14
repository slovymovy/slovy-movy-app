package com.slovy.slovymovyapp.test

import com.slovy.slovymovyapp.data.export.AppDataExportResult
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.AppDatabaseHolder
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.RemoteDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlin.test.AfterTest

expect object TestContext {
    fun androidContext(): Any?
    fun getCiEnv(name: String): String?
    fun testServerHost(): String
    suspend fun runInExportTestEnvironment(block: suspend () -> Unit)
    fun exportArtifactExists(result: AppDataExportResult): Boolean
    fun deleteExportArtifact(result: AppDataExportResult)
}

/**
 * Base class for tests that provides common database holder management and cleanup.
 * Subclasses can use [testDataDbManager] and [testAppDatabaseHolder] for lazy initialization.
 * All holders are automatically closed in [baseCleanup].
 */
abstract class BaseTestImpl {
    var mgrHolder: TestDataDbManagerHolder? = null
    var appDbHolder: AppDatabaseHolder? = null

    @AfterTest
    fun baseCleanup() {
        mgrHolder?.close()
        mgrHolder = null
        appDbHolder?.close()
        appDbHolder = null
    }

    /**
     * Returns a lazily-initialized [DataDbManager] for tests.
     * The underlying holder is automatically cleaned up after test completion.
     */
    fun testDataDbManager(): DataDbManager {
        val holder = mgrHolder ?: testDataDbManagerHolder().also { mgrHolder = it }
        return holder.manager
    }

    /**
     * Returns a lazily-initialized [LocalDbManager] for tests.
     * The underlying holder is automatically cleaned up after test completion.
     */
    fun testLocalDbManager(): LocalDbManager {
        val holder = mgrHolder ?: testDataDbManagerHolder().also { mgrHolder = it }
        return holder.localDbManager
    }

    /**
     * Returns a lazily-initialized [AppDatabaseHolder] for tests.
     * The holder is automatically cleaned up after test completion.
     */
    fun testAppDatabaseHolder(): AppDatabaseHolder {
        return appDbHolder ?: DataDbManager.openAppDatabaseHolder(testPlatformDbSupport()).also {
            appDbHolder = it
        }
    }
}

expect abstract class BaseTest() : BaseTestImpl

fun testPlatformDbSupport(): PlatformDbSupport = PlatformDbSupport(TestContext.androidContext())

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class IgnoreIos()

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class IgnoreRobolectric()

/**
 * Holder for test DataDbManager that allows proper cleanup.
 * Call [close] to release all database connections.
 */
class TestDataDbManagerHolder(
    val manager: DataDbManager,
    val localDbManager: LocalDbManager,
    private val appDbHolder: AppDatabaseHolder
) {
    fun close() {
        manager.closeAllReadOnlyDatabases()
        localDbManager.closeAll()
        appDbHolder.close()
    }
}

fun testDataDbManagerHolder(): TestDataDbManagerHolder {
    val platform = testPlatformDbSupport()
    val holder = DataDbManager.openAppDatabaseHolder(platform)
    val settingRepo = SettingsRepository(holder.database)
    val mgr = DataDbManager(platform, settingRepo, testRemoteDataProvider())
    val localMgr = LocalDbManager(platform)
    return TestDataDbManagerHolder(mgr, localMgr, holder)
}

fun testRemoteDataProvider(): RemoteDataProvider {
    val port = TestContext.getCiEnv("TEST_SERVER_PORT") ?: 9090
    val host = TestContext.testServerHost()
    return TestServerDataProvider(baseUrl = "http://$host:$port")
}

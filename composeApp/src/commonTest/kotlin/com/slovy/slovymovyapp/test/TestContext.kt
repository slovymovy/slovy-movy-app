package com.slovy.slovymovyapp.test

import com.slovy.slovymovyapp.data.remote.AppDatabaseHolder
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GithubRepoDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlin.test.AfterTest

expect object TestContext {
    fun androidContext(): Any?
    fun getCiEnv(name: String): String?
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
    private val appDbHolder: AppDatabaseHolder
) {
    fun close() {
        manager.closeAllReadOnlyDatabases()
        appDbHolder.close()
    }
}

fun testDataDbManagerHolder(): TestDataDbManagerHolder {
    val platform = testPlatformDbSupport()
    val holder = DataDbManager.openAppDatabaseHolder(platform)
    val settingRepo = SettingsRepository(holder.database)
    val mgr = DataDbManager(platform, settingRepo, testRemoteDataProvider())
    return TestDataDbManagerHolder(mgr, holder)
}

const val gitBranchRefEnv = "GIT_BRANCH_REF"

fun testRemoteDataProvider(): GithubRepoDataProvider {
    return GithubRepoDataProvider(
        ref = TestContext.getCiEnv(gitBranchRefEnv) ?: "main",
        authToken = TestContext.getCiEnv("ACCESS_TO_GH_TOKEN")
    )
}

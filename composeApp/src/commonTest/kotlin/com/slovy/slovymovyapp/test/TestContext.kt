package com.slovy.slovymovyapp.test

import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GithubRepoDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository

expect object TestContext {
    fun androidContext(): Any?
    fun getCiEnv(name: String): String?
}

expect abstract class BaseTest()

fun testPlatformDbSupport(): PlatformDbSupport = PlatformDbSupport(TestContext.androidContext())

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class IgnoreIos()

fun testDataDbManager(): DataDbManager {
    val platform = testPlatformDbSupport()
    val db = DataDbManager.openAppDatabase(platform)
    val settingRepo = SettingsRepository(db)
    val mgr = DataDbManager(platform, settingRepo, testRemoteDataProvider())
    return mgr
}

const val gitBranchRefEnv = "GIT_BRANCH_REF"

fun testRemoteDataProvider(): GithubRepoDataProvider {
    return GithubRepoDataProvider(
        ref = TestContext.getCiEnv(gitBranchRefEnv) ?: "main",
        authToken = TestContext.getCiEnv("ACCESS_TO_GH_TOKEN")
    )
}

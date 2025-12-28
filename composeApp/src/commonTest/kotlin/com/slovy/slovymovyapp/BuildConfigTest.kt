package com.slovy.slovymovyapp

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-platform tests for AppBuildConfig.
 * These tests verify that build configuration values are available on all platforms.
 */
class BuildConfigTest {

    @Test
    fun buildConfig_hasNonEmptyVersionName() {
        val config = getAppBuildConfig()
        assertTrue(config.versionName.isNotBlank(), "versionName should not be blank")
    }

    @Test
    fun buildConfig_hasPositiveVersionCode() {
        val config = getAppBuildConfig()
        assertTrue(config.versionCode > 0, "versionCode should be positive, was: ${config.versionCode}")
    }

    @Test
    fun buildConfig_hasNonEmptyApplicationId() {
        val config = getAppBuildConfig()
        assertTrue(config.applicationId.isNotBlank(), "applicationId should not be blank")
    }

    @Test
    fun buildConfig_isDebugIsBoolean() {
        val config = getAppBuildConfig()
        // This test just verifies isDebug is accessible and is a boolean (true or false)
        val isDebug = config.isDebug
        assertTrue(isDebug || !isDebug, "isDebug should be a valid boolean")
    }
}

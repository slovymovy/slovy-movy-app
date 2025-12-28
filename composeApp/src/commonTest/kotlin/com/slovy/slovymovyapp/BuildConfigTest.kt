package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.test.IgnoreIos
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-platform tests for AppBuildConfig.
 * These tests verify that build configuration values are available on all platforms.
 */
class BuildConfigTest {

    @IgnoreIos
    @Test
    fun buildConfig_hasNonEmptyVersionName() {
        val config = getAppBuildConfig()
        assertTrue(config.versionName.isNotBlank(), "versionName should not be blank")
    }

    @IgnoreIos
    @Test
    fun buildConfig_hasPositiveVersionCode() {
        val config = getAppBuildConfig()
        assertTrue(config.versionCode > 0, "versionCode should be positive, was: ${config.versionCode}")
    }

    @IgnoreIos
    @Test
    fun buildConfig_hasNonEmptyApplicationId() {
        val config = getAppBuildConfig()
        assertTrue(config.applicationId.isNotBlank(), "applicationId should not be blank")
    }
}

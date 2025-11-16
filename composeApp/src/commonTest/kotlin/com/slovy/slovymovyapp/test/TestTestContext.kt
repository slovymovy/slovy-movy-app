package com.slovy.slovymovyapp.test

import kotlin.test.Test
import kotlin.test.assertEquals

class TestTestContext : BaseTest() {
    @Test
    fun isTestIsWorking() {
        val isTest = TestContext.getCiEnv("IS_TEST")
        assertEquals("true", isTest)
    }
}
package com.slovy.slovymovyapp.test

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

actual object TestContext {
    actual fun androidContext(): Any? {
        return ApplicationProvider.getApplicationContext()
    }

    actual fun getCiEnv(name: String): String? {
        val env = InstrumentationRegistry.getArguments().getCharSequence(name) as String?
        return if (env.isNullOrEmpty()) null else env
    }

    actual fun testServerHost(): String = "10.0.2.2"
}

@RunWith(AndroidJUnit4::class)
actual abstract class BaseTest actual constructor() : BaseTestImpl()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreIos actual constructor()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreRobolectric actual constructor()

actual fun testAssume(condition: Boolean, message: String) {
    org.junit.Assume.assumeTrue(message, condition)
}

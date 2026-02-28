package com.slovy.slovymovyapp.test

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getCiEnv(name: String): String? {
        val env = getenv(name)?.toKString()
        return if (env.isNullOrEmpty()) null else env
    }

    actual fun testServerHost(): String = "127.0.0.1"
}

actual abstract class BaseTest actual constructor() : BaseTestImpl()

actual typealias IgnoreIos = kotlin.test.Ignore

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreRobolectric actual constructor()

// @IgnoreIos on the enclosing class already prevents this from running on iOS.
actual fun testAssume(condition: Boolean, message: String) = Unit

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
        return getenv(name)?.toKString()
    }
}

actual abstract class BaseTest actual constructor()

actual typealias IgnoreIos = kotlin.test.Ignore
